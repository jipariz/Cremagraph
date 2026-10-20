package app.onlyshots.record

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.ReadingTile
import app.onlyshots.core.designsystem.component.RecordButton
import app.onlyshots.core.designsystem.component.RecordButtonState
import app.onlyshots.core.designsystem.component.RuleStatusRow
import app.onlyshots.core.designsystem.component.RulesStrip
import app.onlyshots.core.designsystem.component.RulesStripHint
import app.onlyshots.core.designsystem.component.SourceChip
import app.onlyshots.core.designsystem.component.StatusChip
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.record_armed
import app.onlyshots.core.resources.record_no_data_hint
import app.onlyshots.core.resources.record_ready
import app.onlyshots.core.resources.record_settings
import app.onlyshots.core.resources.record_simulate_shot
import app.onlyshots.core.resources.record_status_no_machine
import app.onlyshots.core.resources.record_stops
import app.onlyshots.core.resources.text
import app.onlyshots.machineLabel
import org.jetbrains.compose.resources.stringResource

/** What the panels around the camera share, so each posture's panel is a few lines of layout. */
internal class TakeInputs(
    val state: RecordUiState,
    val camera: CameraState,
    val settings: RecordingSettings,
    val live: Sample?,
    val actions: RecordActions,
    val canSimulateShot: Boolean,
    val saveError: UiText?,
    val onBack: () -> Unit,
    val onSettings: () -> Unit,
) {
    /** "simulate shot" can pull the replayed shot: armed, with the camera. */
    val canSimulate: Boolean
        get() = canSimulateShot && state.phase == RecordPhase.ARMED && !state.noCamera

    /** The newest reading: the shot's while recording, the source's before. */
    val sample: Sample?
        get() = if (state.recording) state.shot.samples.lastOrNull() ?: live else live
}

/** Back (where the panel has it), the source with its live dot, and the recording settings on the right. */
@Composable
internal fun TakeTopRow(take: TakeInputs, back: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back) BackButton(onClick = take.onBack)
        val source = machineLabel(take.state.sourceKind)
        SourceChip(
            if (take.state.hasData) source else stringResource(Res.string.record_status_no_machine),
            live = take.state.hasData,
        )
        Spacer(Modifier.weight(1f))
        // Start, pre-roll and stop rules, as the phone's gear: they need a machine and are closed mid-shot.
        PopIn(take.state.hasData && !take.state.recording) {
            IconCircleButton(CgIcons.Settings, stringResource(Res.string.record_settings), onClick = take.onSettings)
        }
    }
}

/** The readings as tiles, [columns] to a row, coloured as the graph draws them. */
@Composable
internal fun ReadingsGrid(take: TakeInputs, columns: Int, large: Boolean, only: Set<Quantity>? = null) {
    val readings = takeReadings(take.sample, take.state.sourceKind).filter { only == null || it.quantity in only }
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        for (row in readings.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                for (r in row) ReadingTile(
                    r.label.text(),
                    r.value,
                    stringResource(r.unit),
                    Modifier.weight(1f),
                    r.quantity.color(),
                    large,
                )
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** How the take starts and stops: done or waiting while it runs, the rules themselves before. */
@Composable
internal fun RulesBlock(take: TakeInputs) {
    val settings = take.settings
    val stop = RecordSession.stopRuleShort(settings.effectiveFor(take.state.sourceKind).stopRule).text()
    val mode =
        when {
            take.state.recording -> RulesMode.RUNNING
            take.state.hasData -> RulesMode.RULES
            else -> RulesMode.HINT
        }
    // The rules turn into their progress when recording starts, with a cross-fade and the block easing to its height.
    AnimatedContent(
        mode,
        transitionSpec = {
            fadeIn(CgMotion.medium()) togetherWith
                fadeOut(CgMotion.short()) using
                SizeTransform { _, _ -> CgMotion.sizeSpec }
        },
        label = "rules",
    ) { shown ->
        RulesBlockContent(take, shown, stop)
    }
}

private enum class RulesMode {
    RUNNING,
    RULES,
    HINT,
}

@Composable
private fun RulesBlockContent(take: TakeInputs, mode: RulesMode, stop: String) {
    val settings = take.settings
    if (mode == RulesMode.RUNNING) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CgColors.Surface.Base)) {
            RuleStatusRow(startRuleLine(settings).text(), done = take.state.shot.samples.isNotEmpty())
            RuleStatusRow(stringResource(Res.string.record_stops, stop), done = false)
        }
    } else if (mode == RulesMode.RULES) {
        val (start, startDetail) = RecordSession.startRuleShort(settings)
        RulesStrip(start.text(), startDetail?.text(), stop, Modifier.clip(RoundedCornerShape(12.dp)))
    } else {
        RulesStripHint(stringResource(Res.string.record_no_data_hint), Modifier.clip(RoundedCornerShape(12.dp)))
    }
}

/** The clock and the quality on the left, the record button on the right; "simulate shot" in a debug build. */
@Composable
internal fun TakeFooter(take: TakeInputs) {
    val state = take.state
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // While filming, the take's one clock (the brew's, once the shot runs); before, a word says where
                // things stand. One gives way to the other with a fade.
                AnimatedContent(
                    state.recording,
                    transitionSpec = {
                        fadeIn(CgMotion.medium()) togetherWith
                            fadeOut(CgMotion.short()) using
                            SizeTransform { _, _ -> CgMotion.sizeSpec }
                    },
                    label = "clock",
                ) { recording ->
                    if (recording) Text(state.status.text(), style = CgTheme.type.cardTitle)
                    else
                        Text(
                            when {
                                state.phase == RecordPhase.ARMED -> stringResource(Res.string.record_armed)
                                else -> stringResource(Res.string.record_ready)
                            },
                            style = CgTheme.type.cardTitle,
                        )
                }
                Text(
                    takeQuality(take.camera.supports4k, take.camera.micOn).text(),
                    style = CgTheme.type.caption,
                    color = CgColors.Text.Secondary,
                )
            }
            RecordButton(state.buttonState(), onClick = take.actions::recordPressed)
        }
        AnimatedVisibility(
            take.canSimulate,
            enter = fadeIn(CgMotion.medium()) + expandVertically(CgMotion.sizeSpec),
            exit = fadeOut(CgMotion.short()) + shrinkVertically(CgMotion.sizeSpec),
        ) {
            CgButton(
                stringResource(Res.string.record_simulate_shot),
                onClick = take.actions::simulateShot,
                kind = CgButtonKind.Secondary,
            )
        }
    }
}

/**
 * The panel's column: what scrolls on top, and [TakeFooter] pinned at the bottom, so the record button stays where the
 * thumb expects it.
 */
@Composable
internal fun PanelColumn(
    modifier: Modifier = Modifier,
    footer: TakeInputs? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(CgSpace.S16, CgSpace.S16, CgSpace.S16, CgSpace.S24)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
        footer?.let {
            Spacer(Modifier.size(CgSpace.S16))
            TakeFooter(it)
        }
    }
}

/**
 * A book foldable resting on its side: under the camera, the source, the rules and the readings that matter before a
 * shot, and the button.
 */
@Composable
internal fun TabletopPanel(take: TakeInputs, modifier: Modifier = Modifier) {
    PanelColumn(modifier, footer = take) {
        TakeTopRow(take, back = true)
        RulesBlock(take)
        // Recording, every reading; before, the two that matter, faded one into the other.
        AnimatedContent(
            take.state.recording,
            transitionSpec = {
                fadeIn(CgMotion.medium()) togetherWith
                    fadeOut(CgMotion.short()) using
                    SizeTransform { _, _ -> CgMotion.sizeSpec }
            },
            label = "tabletop",
        ) { recording ->
            if (recording) ReadingsGrid(take, columns = 4, large = false)
            else ReadingsGrid(take, columns = 2, large = false, only = setOf(Quantity.Temperature, Quantity.Weight))
        }
        Banners(take.camera, take.saveError)
    }
}

/**
 * A flip in flex mode: the camera stands in the upper half, and the lower half is the controls. Back, the source and
 * the graph, the readings, the rules, and the clock with the button.
 */
@Composable
internal fun FlexPanel(take: TakeInputs, modifier: Modifier = Modifier) {
    PanelColumn(modifier, footer = take) {
        TakeTopRow(take, back = true)
        ReadingsGrid(take, columns = 2, large = false)
        RulesBlock(take)
        Banners(take.camera, take.saveError)
    }
}

/** The colour the graph draws this quantity in. */
private fun Quantity.color(): Color =
    when (this) {
        Quantity.Pressure -> CgColors.Series.Pressure
        Quantity.Flow -> CgColors.Series.Flow
        Quantity.Weight -> CgColors.Text.Primary
        Quantity.WeightFlow -> CgColors.Series.WeightFlow
        Quantity.Temperature -> CgColors.Series.Temperature
    }

/**
 * Whether the status chip says anything the screen does not already: without a machine it would only say so again,
 * beside the source's chip and the hint over the button.
 */
internal val RecordUiState.statusWorthShowing: Boolean
    get() = hasData || recording || noCamera

/** The record button's look for the session's phase. */
internal fun RecordUiState.buttonState(): RecordButtonState =
    when {
        recording -> RecordButtonState.Recording
        noCamera -> RecordButtonState.Disabled
        phase == RecordPhase.ARMED -> RecordButtonState.Armed
        else -> RecordButtonState.Idle
    }

/**
 * The full-screen camera's controls, floating over the picture on soft shades: back, the source and the settings along
 * the top; the rules and the record button along the bottom held upright, the button down the trailing edge on its
 * side. [zoom] is the lenses' pill: over the rules held upright, beside the button (standing, `vertical`) on its side.
 *
 * [onClear] gets what of the window, in root px, the controls leave free: the graph is kept there.
 */
@Composable
internal fun FloatingControls(
    take: TakeInputs,
    landscape: Boolean,
    /** The graph is in the upper half: the status chip goes over the bottom controls, out of its way. */
    graphOnTop: Boolean,
    zoom: @Composable (Modifier, vertical: Boolean) -> Unit,
    onClear: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = take.state
    var top by remember { mutableStateOf(Rect.Zero) }
    var bottom by remember { mutableStateOf(Rect.Zero) }
    var side by remember { mutableStateOf(Rect.Zero) }
    var window by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(top, bottom, side, window, landscape) {
        if (window.isEmpty) return@LaunchedEffect
        onClear(
            Rect(
                window.left,
                top.bottom.coerceAtLeast(window.top),
                if (landscape && !side.isEmpty) side.left else window.right,
                if (bottom.isEmpty) window.bottom else bottom.top,
            )
        )
    }
    val status =
        @Composable { m: Modifier ->
            if (state.statusWorthShowing)
                StatusChip(
                    text = state.status.text(),
                    recording = state.recording,
                    dot = if (state.recording) CgColors.Text.Primary else null,
                    modifier = m,
                )
        }
    // The take's time is the status chip's, one clock: here only "simulate shot", where there is one.
    val simulate =
        @Composable {
            if (!state.recording && take.canSimulate) {
                CgButton(
                    stringResource(Res.string.record_simulate_shot),
                    onClick = take.actions::simulateShot,
                    kind = CgButtonKind.Secondary,
                )
            }
        }
    Box(modifier.fillMaxSize().onGloballyPositioned { window = it.boundsInRoot() }) {
        Box(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(TOP_SHADE)
                .background(Brush.verticalGradient(listOf(SHADE, Color.Transparent)))
        )
        if (landscape)
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(SIDE_SHADE)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, SHADE)))
            )
        else
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BOTTOM_SHADE)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, SHADE)))
            )
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(CgSpace.S12)) {
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
            ) {
                Box(Modifier.onGloballyPositioned { top = it.boundsInRoot() }) { TakeTopRow(take, back = true) }
                Banners(take.camera, take.saveError)
                if (!graphOnTop) status(Modifier)
            }
            if (landscape) {
                // The lenses to the button's start, where the thumb is.
                Row(
                    Modifier.align(Alignment.CenterEnd).onGloballyPositioned { side = it.boundsInRoot() },
                    horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    zoom(Modifier, true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
                    ) {
                        RecordButton(state.buttonState(), onClick = take.actions::recordPressed)
                        simulate()
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomStart).widthIn(max = RULES_WIDTH).onGloballyPositioned {
                        bottom = it.boundsInRoot()
                    },
                    verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
                ) {
                    if (graphOnTop) status(Modifier)
                    RulesBlock(take)
                }
            } else {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
                ) {
                    if (graphOnTop) status(Modifier.align(Alignment.Start))
                    Column(
                        Modifier.onGloballyPositioned { bottom = it.boundsInRoot() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
                    ) {
                        // Where the camera apps have them: just over the shutter.
                        zoom(Modifier, false)
                        Box(Modifier.widthIn(max = RULES_WIDTH)) { RulesBlock(take) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { simulate() }
                            RecordButton(state.buttonState(), onClick = take.actions::recordPressed)
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** The shades behind the floating controls, so they read over a bright picture. */
private val SHADE = Color.Black.copy(alpha = 0.55f)
private val TOP_SHADE = 120.dp
private val BOTTOM_SHADE = 260.dp
private val SIDE_SHADE = 200.dp

/** The rules strip at most: on a tablet it does not run across the whole picture. */
private val RULES_WIDTH = 480.dp
