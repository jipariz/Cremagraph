package app.onlyshots

import app.onlyshots.core.model.StopRule
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_rule_fixed
import app.onlyshots.core.resources.record_rule_length
import app.onlyshots.core.resources.record_rule_manual
import app.onlyshots.core.resources.record_rule_quiet_for
import app.onlyshots.core.resources.record_rule_shot_end
import app.onlyshots.core.resources.record_rule_tail
import app.onlyshots.core.resources.record_rule_weight
import org.jetbrains.compose.resources.StringResource

/**
 * The four stop rules as a picker (the record screen's settings, Home's next take); each remembers the default its
 * parameter starts from.
 */
enum class StopRuleChoice(val label: StringResource, val parameter: StringResource?) {
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
