package app.onlyshots.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.onlyshots.StopRuleChoice
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgStepper
import app.onlyshots.core.designsystem.component.HairlineGrid
import app.onlyshots.core.designsystem.component.ReadingTile
import app.onlyshots.core.designsystem.component.RecordAction
import app.onlyshots.core.designsystem.component.RuleRow
import app.onlyshots.core.designsystem.component.SegmentedControl
import app.onlyshots.core.designsystem.component.SettingRow
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.model.stepLabel
import app.onlyshots.core.model.stepped
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_next_take
import app.onlyshots.core.resources.home_rule_pre_roll
import app.onlyshots.core.resources.home_rule_start
import app.onlyshots.core.resources.home_rule_stop
import app.onlyshots.core.resources.record_pre_roll_hint
import app.onlyshots.core.resources.record_pre_roll_hint_scale
import app.onlyshots.core.resources.record_seconds
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.stringResource

/**
 * A wide Home's second page: the take the record button starts. The source and its live readings, how the take starts
 * and stops (the record screen's own settings), and the record button at the bottom.
 */
@Composable
internal fun NextTakePane(
    state: HomeUiState,
    take: () -> NextTake,
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit,
    onOpenCamera: () -> Unit,
    onSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only a change of the rules recomposes the pane; the live reading is read by LiveTake alone.
    val settings by remember(take) { derivedStateOf { take().settings } }
    // Measured for fit: a short panel (a tablet on its side) tightens up, so the rules still show above the button.
    BoxWithConstraints(
        modifier.fillMaxSize().drawBehind {
            drawLine(CgColors.Line.Hairline, Offset.Zero, Offset(0f, size.height), CgStroke.Hairline.toPx())
        }
    ) {
        val compact = maxHeight < COMPACT_PANEL_HEIGHT
        val gap = if (compact) CgSpace.S12 else ROOMY_GAP
        Column(
            Modifier.fillMaxSize().padding(if (compact) CgSpace.S16 else CgSpace.S24),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                TakeHeading(takeTitle(state.saved), compact)
                LiveTake(state, take, compact)
                TakeRules(settings, state.saved?.kind, onRecording)
            }
            val action = recordAction(state, nextTake = settings)
            RecordAction(
                text = action.text.text(),
                caption = action.caption.text(),
                enabled = action.enabled,
                onClick = onOpenCamera,
                modifier = Modifier.testTag(TestTags.HomeRecordAction),
                withData = action.withData,
            )
            SampleLink(onSample, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** "Next take" over the source's name, its kind dimmed after a dot. */
@Composable
private fun TakeHeading(title: TakeTitle, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
        SectionLabel(stringResource(Res.string.home_next_take))
        val name = title.name.text()
        val kind = title.kind?.text()
        Text(
            buildAnnotatedString {
                append(name)
                if (kind != null) {
                    withStyle(SpanStyle(color = CgColors.Text.Secondary)) {
                        append(" · ")
                        append(kind)
                    }
                }
            },
            style = if (compact) CgTheme.type.cardTitle else CgTheme.type.pivot,
        )
    }
}

/** The next take's source as it is now: its status line and reading tiles, the only part that follows live samples. */
@Composable
private fun LiveTake(state: HomeUiState, take: () -> NextTake, compact: Boolean, modifier: Modifier = Modifier) {
    val live = take().live
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LIVE_GAP)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Read while drawing: the dot's change of colour redraws it, it does not recompose the pane.
            val dot =
                animateColorAsState(
                    if (takeIsLive(state, live)) CgColors.Status.Live else CgColors.Text.Dim,
                    CgMotion.medium(),
                )
            Box(Modifier.size(CgSpace.S6).drawBehind { drawRect(dot.value) })
            Text(
                takeStatus(state, live).text(),
                style = CgTheme.type.caption,
                color = CgColors.Text.Secondary,
            )
        }
        val readings = takeReadings(state, live)
        // The tiles open under the status once the machine sends its first reading.
        var shown by remember { mutableStateOf(readings) }
        if (readings.isNotEmpty()) shown = readings
        AnimatedVisibility(
            readings.isNotEmpty(),
            enter = CgMotion.reveal,
            exit = CgMotion.conceal,
        ) {
            // One grid: the tiles meet on single hairlines.
            HairlineGrid(shown.size, columns = shown.size) { i, cell ->
                val r = shown[i]
                ReadingTile(
                    stringResource(r.label),
                    r.value,
                    stringResource(r.unit),
                    cell,
                    color = r.kind.color(),
                    large = !compact,
                    framed = false,
                )
            }
        }
    }
}

/**
 * How the next take starts and stops, all of it, as the record screen's settings have it: the switches, the pre-roll,
 * and while the take stops by itself, on what and after how long. Worded for the [kind] of source: a scale has no pump.
 */
@Composable
private fun TakeRules(
    settings: RecordingSettings,
    kind: SourceKind?,
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = kind == SourceKind.SCALE
    val effective = settings.effective(kind)
    // One framed list, the rules divided by the panel's rule colour, as the canvas lists them.
    Column(modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline)) {
        RuleRow(
            stringResource(Res.string.home_rule_start),
            startRuleDetail(settings, kind).text(),
            checked = settings.autoStart,
            onToggle = { on -> onRecording { it.copy(autoStart = on) } },
            modifier = ruleCell,
        )
        RuleDivider()
        SettingRow(
            stringResource(Res.string.home_rule_pre_roll),
            ruleCell,
            hint =
                stringResource(if (scale) Res.string.record_pre_roll_hint_scale else Res.string.record_pre_roll_hint),
        ) {
            CgStepper(
                value = stringResource(Res.string.record_seconds, settings.preRollSeconds.toString()),
                onMinus = { onRecording { it.withPreRollStep(-1) } },
                onPlus = { onRecording { it.withPreRollStep(1) } },
                minusEnabled = settings.canStepPreRoll(-1),
                plusEnabled = settings.canStepPreRoll(1),
            )
        }
        RuleDivider()
        RuleRow(
            stringResource(Res.string.home_rule_stop),
            stopRuleDetail(settings, kind).text(),
            checked = settings.stopRule != StopRule.Manual,
            onToggle = { on -> onRecording { it.withStopAfterShot(on) } },
            modifier = ruleCell,
        )
        if (effective.stopRule != StopRule.Manual) {
            RuleDivider()
            StopRulePicker(effective, kind, onRecording, ruleCell)
        }
    }
}

/** The rules that stop by themselves ("by hand" is the switch above), and the chosen one's parameter. */
@Composable
private fun StopRulePicker(
    effective: RecordingSettings,
    kind: SourceKind?,
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = stopRuleChoices(kind)
    val current = StopRuleChoice.of(effective.stopRule)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        SegmentedControl(
            fill = true,
            options = choices.map { stringResource(it.label) },
            selectedIndex = choices.indexOf(current),
            onSelect = { i -> onRecording { it.copy(stopRule = choices[i].rule()) } },
        )
        val label = effective.stopRule.stepLabel
        val parameter = current.parameter
        if (label != null && parameter != null) {
            SettingRow(stringResource(parameter).replaceFirstChar { it.uppercase() }) {
                // Stepped from what is shown, so a scale's stand-in "weight settles" becomes the saved rule.
                CgStepper(
                    value = label,
                    onMinus = { onRecording { s -> s.copy(stopRule = s.effective(kind).stopRule.stepped(-1)) } },
                    onPlus = { onRecording { s -> s.copy(stopRule = s.effective(kind).stopRule.stepped(1)) } },
                )
            }
        }
    }
}

/** A cell of the take's framed rules: the canvas's 12 × 16 padding. */
private val ruleCell = Modifier.padding(horizontal = CgSpace.S16, vertical = CgSpace.S12)

/** The rule between two of the take's rules. */
@Composable
private fun RuleDivider() {
    Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Divider))
}

/** The colour the graph draws this quantity in, so a tile reads as its line (as the record screen's tiles do). */
private fun ReadingKind.color(): Color =
    when (this) {
        ReadingKind.Temperature -> CgColors.Series.Temperature
        ReadingKind.Pressure -> CgColors.Series.Pressure
        ReadingKind.Weight -> CgColors.Series.Weight
        ReadingKind.Flow -> CgColors.Series.Flow
        ReadingKind.WeightFlow -> CgColors.Series.WeightFlow
    }

/**
 * Under this the panel is short (a tablet on its side) and tightens up. A measure of the panel's own content, taken
 * where it is placed, so it stays here rather than with the window's breakpoints.
 */
private val COMPACT_PANEL_HEIGHT = 640.dp

/** The gap between the pane's groups when the panel is tall enough. */
private val ROOMY_GAP = 20.dp

/** Between the take's status line and its tiles. */
private val LIVE_GAP = 10.dp
