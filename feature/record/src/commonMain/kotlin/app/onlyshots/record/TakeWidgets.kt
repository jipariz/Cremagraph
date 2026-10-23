package app.onlyshots.record

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgMotion.tick
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.HairlineGrid
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.ReadingTile
import app.onlyshots.core.designsystem.component.RecordButton
import app.onlyshots.core.designsystem.component.RuleStatusRow
import app.onlyshots.core.designsystem.component.RulesStripHint
import app.onlyshots.core.designsystem.component.SourceChip
import app.onlyshots.core.designsystem.component.StatusChip
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_armed
import app.onlyshots.core.resources.record_no_data_hint
import app.onlyshots.core.resources.record_ready
import app.onlyshots.core.resources.record_rule_start
import app.onlyshots.core.resources.record_rule_stop
import app.onlyshots.core.resources.record_settings
import app.onlyshots.core.resources.record_simulate_shot
import app.onlyshots.core.resources.record_status_no_machine
import app.onlyshots.core.resources.record_stops
import app.onlyshots.core.resources.text
import app.onlyshots.machineLabel
import org.jetbrains.compose.resources.stringResource

/*
 * The take's widgets, shared by every posture's panel and by the floating controls: each layout arranges these.
 */

/** Back (where the panel has it), the source with its live dot, and the recording settings on the right. */
@Composable
internal fun TakeTopRow(take: TakeInputs, back: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mid-take there is no leaving: the key stays, inert, until the take is saved.
        if (back) BackButton(onClick = take.onBack, enabled = !take.state.recording)
        val source = machineLabel(take.state.sourceKind)
        SourceChip(
            if (take.state.hasData) source else stringResource(Res.string.record_status_no_machine),
            live = take.state.hasData,
        )
        Spacer(Modifier.weight(1f))
        // Start, pre-roll and stop rules, as the phone's gear: they need a machine and a camera, and are closed
        // mid-shot.
        KeyIn(take.state.hasData && !take.state.recording && !take.state.noCamera) {
            IconCircleButton(
                CgIcons.Settings,
                stringResource(Res.string.record_settings),
                onClick = take.onSettings,
                modifier = Modifier.testTag(TestTags.RecordSettings),
            )
        }
    }
}

/** The readings as tiles, [columns] to a row, coloured as the graph draws them. */
@Composable
internal fun ReadingsGrid(take: TakeInputs, columns: Int, large: Boolean, only: Set<Quantity>? = null) {
    val readings = takeReadings(take.sample, take.state.sourceKind).filter { only == null || it.quantity in only }
    // One grid: the cells meet on single hairlines.
    HairlineGrid(readings.size, columns) { i, cell ->
        val r = readings[i]
        ReadingTile(r.label.text(), r.value, stringResource(r.unit), cell, r.quantity.color(), large, framed = false)
    }
}

/** How the take starts and stops: done or waiting while it runs, the rules themselves before. */
@Composable
internal fun RulesBlock(take: TakeInputs, modifier: Modifier = Modifier) {
    val mode = take.state.rulesMode() ?: return
    val stop = RecordSession.stopRuleShort(take.settings.effectiveFor(take.state.sourceKind).stopRule).text()
    // The rules turn into their progress when recording starts: the block ticks over, like a readout, to its new
    // height.
    AnimatedContent(
        mode,
        transitionSpec = { tick() },
        label = "rules",
        modifier = modifier,
    ) { shown ->
        RulesBlockContent(take, shown, stop)
    }
}

/** The rules as a card over the picture, in a hairline frame. */
@Composable
internal fun RulesCard(take: TakeInputs, modifier: Modifier = Modifier) {
    RulesBlock(take, modifier.border(CgStroke.Hairline, CgColors.Line.Hairline))
}

@Composable
private fun RulesBlockContent(take: TakeInputs, mode: RulesMode, stop: String) {
    val settings = take.settings
    when (mode) {
        RulesMode.RUNNING ->
            Column(Modifier.fillMaxWidth().background(CgColors.Surface.Base)) {
                RuleStatusRow(startRuleLine(settings).text(), done = take.state.shot.samples.isNotEmpty())
                Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Divider))
                RuleStatusRow(stringResource(Res.string.record_stops, stop), done = false)
            }
        RulesMode.RULES -> {
            val (start, startDetail) = RecordSession.startRuleShort(settings)
            RulesGrid(start.text() + startDetail?.text()?.let { " · $it" }.orEmpty(), stop)
        }
        RulesMode.HINT ->
            RulesStripHint(stringResource(Res.string.record_no_data_hint), Modifier.background(CgColors.Surface.Base))
    }
}

/** How the take starts and stops, as two cells side by side with a hairline between: ▶ the start, ■ the stop. */
@Composable
private fun RulesGrid(start: String, stop: String) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(CgColors.Surface.Base).semantics(
            mergeDescendants = true
        ) {}
    ) {
        RuleCell(CgIcons.Play, stringResource(Res.string.record_rule_start), start, Modifier.weight(1f))
        Box(Modifier.width(CgStroke.Hairline).fillMaxHeight().background(CgColors.Line.Hairline))
        RuleCell(CgIcons.Stop, stringResource(Res.string.record_rule_stop), stop, Modifier.weight(1f))
    }
}

/** One rule's cell: what it is in grey (▶ START), and under it the rule itself. */
@Composable
private fun RuleCell(icon: ImageVector, label: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.heightIn(min = CgSize.Touch).padding(horizontal = CgSpace.S16, vertical = RULE_CELL_PADDING),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S4),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S6), verticalAlignment = Alignment.CenterVertically) {
            CgIcon(icon, tint = CgColors.Text.Secondary, size = RULE_ICON_SIZE)
            Text(label.caps(), style = CgTheme.type.data, color = CgColors.Text.Secondary)
        }
        Text(text.caps(), style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp))
    }
}

/**
 * Where the take stands, over the picture: armed, a signal-edged notice (it starts by itself); otherwise the status
 * chip.
 */
@Composable
internal fun TakeStatus(state: RecordUiState, modifier: Modifier = Modifier) {
    val text = state.status.text()
    if (state.phase == RecordPhase.ARMED && !state.recording && !state.noCamera) {
        Text(
            text.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp),
            color = CgColors.Brand.Signal,
            modifier =
                modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .background(CgColors.Surface.Base.copy(alpha = OVER_PICTURE_ALPHA))
                    .border(CgStroke.Hairline, CgColors.Line.Hairline)
                    .padding(horizontal = NOTICE_PADDING, vertical = CgSpace.S6),
        )
    } else {
        StatusChip(
            text = text,
            recording = state.recording,
            dot = if (state.recording) CgColors.Text.Primary else null,
            modifier = modifier,
        )
    }
}

/** The take's status, where it says anything the screen does not already. */
@Composable
internal fun TakeStatusIfWorthIt(state: RecordUiState, modifier: Modifier = Modifier) {
    if (state.statusWorthShowing) TakeStatus(state, modifier)
}

/** The record button, its look following the session's phase. */
@Composable
internal fun RecordKey(take: TakeInputs, modifier: Modifier = Modifier) {
    RecordButton(
        take.state.buttonState(),
        onClick = take.actions::recordPressed,
        modifier = modifier.testTag(TestTags.RecordButton),
    )
}

/** "simulate shot": pulls the replayed shot by hand. */
@Composable
internal fun SimulatePill(take: TakeInputs, modifier: Modifier = Modifier) {
    Pill(
        stringResource(Res.string.record_simulate_shot),
        onClick = take.actions::simulateShot,
        modifier = modifier.widthIn(max = SIMULATE_WIDTH).testTag(TestTags.RecordSimulate),
    )
}

/**
 * The clock and the quality on the left, the record button on the right and "simulate shot" beside it, where there is
 * one. [clock] is how large the clock is: as large as the posture's panel has room for. Armed, the word is the signal.
 */
@Composable
internal fun TakeFooter(take: TakeInputs, modifier: Modifier = Modifier, clock: TextStyle = CgTheme.type.cardTitle) {
    val state = take.state
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
            // While filming, the take's one clock (the brew's, once the shot runs); before, a word says where
            // things stand. One ticks over into the other.
            AnimatedContent(
                state.recording,
                transitionSpec = { tick() },
                label = "clock",
            ) { recording ->
                if (recording) Text(state.status.text(), style = clock)
                else {
                    val armed = state.phase == RecordPhase.ARMED
                    Text(
                        stringResource(if (armed) Res.string.record_armed else Res.string.record_ready).caps(),
                        style = clock,
                        color = if (armed) CgColors.Brand.Signal else CgColors.Text.Primary,
                    )
                }
            }
            Text(
                takeQuality(take.camera.supports4k, take.camera.micOn).text(),
                style = CgTheme.type.caption,
                color = CgColors.Text.Secondary,
            )
        }
        RecordKey(take)
        AnimatedVisibility(
            take.canSimulate,
            enter = expandHorizontally(CgMotion.sizeSpec, expandFrom = Alignment.Start),
            exit = shrinkHorizontally(CgMotion.sizeSpec, shrinkTowards = Alignment.Start),
        ) {
            SimulatePill(take)
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
    clock: TextStyle = CgTheme.type.cardTitle,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(CgSpace.S16, CgSpace.S16, CgSpace.S16, CgSpace.S24)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PANEL_SPACING),
            content = content,
        )
        footer?.let {
            // Under a hairline, the clock and the button: where the thumb expects them.
            Spacer(Modifier.size(CgSpace.S12))
            Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
            Spacer(Modifier.size(CgSpace.S12))
            TakeFooter(it, clock = clock)
        }
    }
}

/** The colour the graph draws this quantity in (as Home's "next take" tiles do). */
private fun Quantity.color(): Color =
    when (this) {
        Quantity.Pressure -> CgColors.Series.Pressure
        Quantity.Flow -> CgColors.Series.Flow
        Quantity.Weight -> CgColors.Series.Weight
        Quantity.WeightFlow -> CgColors.Series.WeightFlow
        Quantity.Temperature -> CgColors.Series.Temperature
    }

/** How opaque a notice's ground is over the picture: it reads over a bright one, and still shows it. */
internal const val OVER_PICTURE_ALPHA = 0.85f

/** "simulate shot" at most: two short lines beside the record key. */
private val SIMULATE_WIDTH = 120.dp

/** The gap between a panel's blocks. */
private val PANEL_SPACING = 18.dp

/** A rule cell's padding above and below, with [RULE_ICON_SIZE] icons: the two lines fill a touch target. */
private val RULE_CELL_PADDING = 10.dp
private val RULE_ICON_SIZE = 10.dp

/** The armed notice's padding at its sides. */
private val NOTICE_PADDING = 10.dp
