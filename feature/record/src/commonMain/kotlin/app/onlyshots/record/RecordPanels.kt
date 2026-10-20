package app.onlyshots.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.FormatOption
import app.onlyshots.core.designsystem.component.FormatPicker
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.ReadingTile
import app.onlyshots.core.designsystem.component.RecordButton
import app.onlyshots.core.designsystem.component.RecordButtonState
import app.onlyshots.core.designsystem.component.RuleStatusRow
import app.onlyshots.core.designsystem.component.RulesStrip
import app.onlyshots.core.designsystem.component.RulesStripHint
import app.onlyshots.core.designsystem.component.SourceChip
import app.onlyshots.core.designsystem.component.TakeNotice
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.formatClock
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.record_armed
import app.onlyshots.core.resources.record_button_returns
import app.onlyshots.core.resources.record_camera_paused
import app.onlyshots.core.resources.record_chosen
import app.onlyshots.core.resources.record_fits_screen
import app.onlyshots.core.resources.record_graph_settings
import app.onlyshots.core.resources.record_keep_format
import app.onlyshots.core.resources.record_needs_side
import app.onlyshots.core.resources.record_needs_upright
import app.onlyshots.core.resources.record_no_data_hint
import app.onlyshots.core.resources.record_paused
import app.onlyshots.core.resources.record_ready
import app.onlyshots.core.resources.record_simulate_shot
import app.onlyshots.core.resources.record_status_no_machine
import app.onlyshots.core.resources.record_stops
import app.onlyshots.core.resources.record_switch_to
import app.onlyshots.core.resources.record_switched_format
import app.onlyshots.core.resources.record_the_way_it_stands
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
    /** The screen stands the other way from the format: nothing can be filmed until it is turned. */
    val wrongWay: Boolean,
    /** The saved format this take does not use (on its side, a tall format records 16:9 this time). */
    val switchedFrom: VideoFormat?,
    val onBack: () -> Unit,
    /** The graph's style, data and text; null when there is nothing to draw or a take is running. */
    val onGraph: (() -> Unit)?,
    val onSettings: () -> Unit,
) {
    /** The newest reading: the shot's while recording, the source's before. */
    val sample: Sample?
        get() = if (state.recording) state.shot.samples.lastOrNull() ?: live else live
}

/** Back (where the panel has it), the source with its live dot, and the graph's settings on the right. */
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
        take.onGraph?.let {
            IconCircleButton(CgIcons.Sliders, stringResource(Res.string.record_graph_settings), onClick = it)
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
    if (take.state.recording) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CgColors.Surface.Base)) {
            RuleStatusRow(startRuleLine(settings).text(), done = take.state.shot.samples.isNotEmpty())
            RuleStatusRow(stringResource(Res.string.record_stops, stop), done = false)
        }
    } else if (take.state.hasData) {
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
                // The clock is the thing to read while filming; before, a word says where things stand.
                if (state.recording) Text(formatClock(state.videoTimeUs), style = CgTheme.type.valueLarge)
                else
                    Text(
                        when {
                            take.wrongWay -> stringResource(Res.string.record_paused)
                            state.phase == RecordPhase.ARMED -> stringResource(Res.string.record_armed)
                            else -> stringResource(Res.string.record_ready)
                        },
                        style = CgTheme.type.cardTitle,
                    )
                Text(
                    takeQuality(take.camera.supports4k, take.camera.micOn).text(),
                    style = CgTheme.type.caption,
                    color = CgColors.Text.Secondary,
                )
            }
            RecordButton(
                state =
                    when {
                        state.recording -> RecordButtonState.Recording
                        take.wrongWay || state.noCamera -> RecordButtonState.Disabled
                        state.phase == RecordPhase.ARMED -> RecordButtonState.Armed
                        else -> RecordButtonState.Idle
                    },
                onClick = take.actions::recordPressed,
            )
        }
        if (take.canSimulateShot && state.phase == RecordPhase.ARMED && !take.wrongWay && !state.noCamera) {
            CgButton(
                stringResource(Res.string.record_simulate_shot),
                onClick = take.actions::simulateShot,
                kind = CgButtonKind.Secondary,
            )
        }
    }
}

/**
 * A book foldable or an iPhone Duo held open: the page beside the camera. The source and the graph's settings, the
 * readings large, the rules, and the clock with the record button at the bottom.
 */
@Composable
internal fun RecordSidePanel(take: TakeInputs, modifier: Modifier = Modifier) {
    PanelColumn(modifier, footer = take) {
        TakeTopRow(take, back = false)
        ReadingsGrid(take, columns = 2, large = true)
        RulesBlock(take)
        Banners(take.camera, take.saveError)
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
 * Held the wrong way (an iPhone Duo's inner display ignores the orientation lock): the format that fits the screen as
 * it stands, offered in place of the chosen one, and the others with what they need.
 */
@Composable
internal fun WrongWayPanel(take: TakeInputs, fits: VideoFormat, modifier: Modifier = Modifier) {
    PanelColumn(modifier, footer = take) {
        TakeTopRow(take, back = false)
        Text(
            stringResource(Res.string.record_the_way_it_stands).uppercase(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
        )
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CgColors.Surface.Raised)
                .padding(CgSpace.S12),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatShape(fits, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
                Text(fits.label, style = CgTheme.type.cardTitle)
                Text(
                    stringResource(Res.string.record_fits_screen, if (take.camera.supports4k) "4K" else "1080p"),
                    style = CgTheme.type.caption,
                    color = CgColors.Text.Secondary,
                )
            }
        }
        Pill(
            stringResource(Res.string.record_switch_to, fits.label),
            onClick = { take.actions.updateSettings { it.withFormat(fits) } },
            light = true,
            fill = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CgColors.Surface.Base)) {
            for (f in VideoFormat.entries.filter { it != fits }) {
                val needs =
                    stringResource(if (f.landscape) Res.string.record_needs_side else Res.string.record_needs_upright)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = CgSpace.S14, vertical = CgSpace.S12),
                    horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(f.label, style = CgTheme.type.label, modifier = Modifier.weight(1f))
                    Text(
                        if (f == take.settings.format) stringResource(Res.string.record_chosen, needs) else needs,
                        style = CgTheme.type.caption,
                        color = CgColors.Text.Secondary,
                    )
                }
            }
        }
        Text(
            stringResource(Res.string.record_button_returns),
            style = CgTheme.type.caption,
            color = CgColors.Text.Dim,
        )
    }
}

/**
 * A book foldable resting on its side: under the camera, the source, the take's format (16:9 by itself this time, with
 * "keep 9:16"), the rules and the readings that matter before a shot, and the button.
 */
@Composable
internal fun TabletopPanel(take: TakeInputs, modifier: Modifier = Modifier) {
    PanelColumn(modifier, footer = take) {
        TakeTopRow(take, back = true)
        take.switchedFrom?.let { saved ->
            TakeNotice(
                stringResource(Res.string.record_switched_format, saved.label),
                action = stringResource(Res.string.record_keep_format, saved.label),
                onAction = take.actions::keepFormat,
            )
        }
        RulesBlock(take)
        if (take.state.recording) ReadingsGrid(take, columns = 4, large = false)
        else {
            ReadingsGrid(take, columns = 2, large = false, only = setOf(Quantity.Temperature, Quantity.Weight))
            FormatPicker(
                FORMAT_OPTIONS,
                VideoFormat.entries.indexOf(take.settings.format),
                onPick = { i -> take.actions.updateSettings { it.withFormat(VideoFormat.entries[i]) } },
                compact = true,
            )
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

/**
 * The camera waits for the screen to turn: dimmed, with what to do in the middle. Drawn over the camera pane in every
 * layout, so the hint is where the eye is.
 */
@Composable
internal fun TurnOverlay(format: VideoFormat, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base.copy(alpha = 0.82f)), Alignment.Center) {
        Column(
            Modifier.padding(CgSpace.S24),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.record_camera_paused).uppercase(),
                style = CgTheme.type.labelStrong,
                color = CgColors.Status.Highlight,
            )
            Text(stringResource(turnTitle(format)), style = CgTheme.type.sheetTitle, textAlign = TextAlign.Center)
            Text(
                turnBody(format).text(),
                style = CgTheme.type.bodySmall,
                color = CgColors.Text.Secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A format's outline at [size] on its long side. */
@Composable
private fun FormatShape(format: VideoFormat, size: androidx.compose.ui.unit.Dp) {
    val w = if (format.landscape) size else size * format.ratio
    val h = if (format.landscape) size / format.ratio else size
    Box(Modifier.size(size), Alignment.Center) {
        Box(Modifier.size(w, h).border(2.dp, CgColors.Text.Secondary, RoundedCornerShape(4.dp)))
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

private val FORMAT_OPTIONS = VideoFormat.entries.map { FormatOption(it.label, it.ratio) }
