package app.onlyshots.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.designsystem.component.CgStepper
import app.onlyshots.core.designsystem.component.CgToggle
import app.onlyshots.core.designsystem.component.SectionHeader
import app.onlyshots.core.designsystem.component.SegmentedControl
import app.onlyshots.core.designsystem.component.SettingRow
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.availableFor
import app.onlyshots.core.model.stepLabel
import app.onlyshots.core.model.stepped
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_auto_start
import app.onlyshots.core.resources.record_auto_start_hint
import app.onlyshots.core.resources.record_format
import app.onlyshots.core.resources.record_format_hint_landscape
import app.onlyshots.core.resources.record_format_hint_portrait
import app.onlyshots.core.resources.record_pre_roll
import app.onlyshots.core.resources.record_pre_roll_hint
import app.onlyshots.core.resources.record_pre_roll_hint_scale
import app.onlyshots.core.resources.record_rule_fixed
import app.onlyshots.core.resources.record_rule_length
import app.onlyshots.core.resources.record_rule_manual
import app.onlyshots.core.resources.record_rule_quiet_for
import app.onlyshots.core.resources.record_rule_shot_end
import app.onlyshots.core.resources.record_rule_tail
import app.onlyshots.core.resources.record_rule_weight
import app.onlyshots.core.resources.record_seconds
import app.onlyshots.core.resources.record_settings_title
import app.onlyshots.core.resources.record_stops_header
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Figma "05 Auto-start and stop": the defaults every new recording starts from. Unavailable while recording, which the
 * record screen enforces by not opening it.
 */
@Composable
fun RecordingSheet(
    settings: RecordingSettings,
    sourceKind: SourceKind,
    onChange: ((RecordingSettings) -> RecordingSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    CgSheet(title = stringResource(Res.string.record_settings_title), onDismiss = onDismiss) {
        RecordingSettingsContent(settings, sourceKind, onChange)
    }
}

/** The recording settings themselves, outside the modal sheet: what a screenshot renders. */
@Composable
fun RecordingSettingsContent(
    settings: RecordingSettings,
    sourceKind: SourceKind,
    onChange: ((RecordingSettings) -> RecordingSettings) -> Unit,
) {
    // What this source really runs with: a scale shows "weight settles" where a machine's "shot end" is saved.
    val effective = settings.effectiveFor(sourceKind)
    val choices = StopRuleChoice.entries.filter { it.rule().availableFor(sourceKind) }
    val scale = sourceKind == SourceKind.SCALE
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S16)) {
        val formats = VideoFormat.entries
        SettingRow(
            stringResource(Res.string.record_format),
            hint =
                stringResource(
                    if (settings.format.landscape) Res.string.record_format_hint_landscape
                    else Res.string.record_format_hint_portrait
                ),
        ) {
            SegmentedControl(
                formats.map { it.label },
                formats.indexOf(settings.format),
                onSelect = { i -> onChange { it.withFormat(formats[i]) } },
            )
        }
        SettingRow(
            stringResource(Res.string.record_auto_start),
            hint = stringResource(Res.string.record_auto_start_hint, RecordSession.triggerText(sourceKind).text()),
        ) {
            CgToggle(settings.autoStart, onCheckedChange = { on -> onChange { it.copy(autoStart = on) } })
        }

        SettingRow(
            stringResource(Res.string.record_pre_roll),
            hint =
                stringResource(if (scale) Res.string.record_pre_roll_hint_scale else Res.string.record_pre_roll_hint),
        ) {
            CgStepper(
                value = stringResource(Res.string.record_seconds, settings.preRollSeconds.toString()),
                onMinus = {
                    onChange {
                        it.copy(preRollSeconds = (it.preRollSeconds - 1).coerceIn(RecordingSettings.PRE_ROLL_RANGE))
                    }
                },
                onPlus = {
                    onChange {
                        it.copy(preRollSeconds = (it.preRollSeconds + 1).coerceIn(RecordingSettings.PRE_ROLL_RANGE))
                    }
                },
                minusEnabled = settings.preRollSeconds > RecordingSettings.PRE_ROLL_RANGE.first,
                plusEnabled = settings.preRollSeconds < RecordingSettings.PRE_ROLL_RANGE.last,
            )
        }

        SectionHeader(stringResource(Res.string.record_stops_header))
        SegmentedControl(
            fill = true,
            options = choices.map { stringResource(it.label) },
            selectedIndex = choices.indexOf(StopRuleChoice.of(effective.stopRule)),
            onSelect = { i -> onChange { it.copy(stopRule = choices[i].rule()) } },
        )
        effective.stopRule.stepLabel?.let { label ->
            SettingRow(
                StopRuleChoice.of(effective.stopRule).parameter?.let { stringResource(it) }.orEmpty(),
                hint = RecordSession.stopRuleText(effective.stopRule).text(),
            ) {
                // Stepped from what is shown, so a scale's stand-in "weight settles" becomes the saved rule.
                CgStepper(
                    value = label,
                    onMinus = { onChange { s -> s.copy(stopRule = s.effectiveFor(sourceKind).stopRule.stepped(-1)) } },
                    onPlus = { onChange { s -> s.copy(stopRule = s.effectiveFor(sourceKind).stopRule.stepped(1)) } },
                )
            }
        }
    }
}

/** The four stop rules as a picker; each remembers the default its parameter starts from. */
private enum class StopRuleChoice(val label: StringResource, val parameter: StringResource?) {
    SHOT_END(Res.string.record_rule_shot_end, Res.string.record_rule_tail),
    WEIGHT(Res.string.record_rule_weight, Res.string.record_rule_quiet_for),
    FIXED(Res.string.record_rule_fixed, Res.string.record_rule_length),
    MANUAL(Res.string.record_rule_manual, null);

    fun rule(): StopRule =
        when (this) {
            SHOT_END -> StopRule.ShotEndPlusTail()
            WEIGHT -> StopRule.WeightSettles()
            FIXED -> StopRule.Fixed()
            MANUAL -> StopRule.Manual
        }

    companion object {
        fun of(rule: StopRule): StopRuleChoice =
            when (rule) {
                is StopRule.ShotEndPlusTail -> SHOT_END
                is StopRule.WeightSettles -> WEIGHT
                is StopRule.Fixed -> FIXED
                StopRule.Manual -> MANUAL
            }
    }
}
