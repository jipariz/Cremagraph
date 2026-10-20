package app.onlyshots.record

import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.record_default_title
import app.onlyshots.core.resources.record_start_before
import app.onlyshots.core.resources.record_start_by_hand
import app.onlyshots.core.resources.record_start_no_pre_roll
import app.onlyshots.core.resources.record_start_with_shot
import app.onlyshots.core.resources.record_started_before
import app.onlyshots.core.resources.record_stop_fixed
import app.onlyshots.core.resources.record_stop_short_fixed
import app.onlyshots.core.resources.record_stop_short_manual
import app.onlyshots.core.resources.record_stop_short_tail
import app.onlyshots.core.resources.record_stop_short_weight
import app.onlyshots.core.resources.text
import kotlin.test.Test
import kotlin.test.assertEquals

class RulesTextTest {
    @Test
    fun startReadsInAFewWords() {
        assertEquals(
            Res.string.record_start_with_shot.text() to Res.string.record_start_before.text("3"),
            RecordSession.startRuleShort(RecordingSettings()),
        )
        assertEquals(
            Res.string.record_start_with_shot.text() to Res.string.record_start_no_pre_roll.text(),
            RecordSession.startRuleShort(RecordingSettings(preRollSeconds = 0)),
        )
        assertEquals(
            Res.string.record_start_by_hand.text() to null,
            RecordSession.startRuleShort(RecordingSettings(autoStart = false)),
        )
        assertEquals(Res.string.record_started_before.text("3"), startRuleLine(RecordingSettings()))
    }

    @Test
    fun everyStopRuleReadsInAFewWords() {
        assertEquals(
            Res.string.record_stop_short_tail.text("5"),
            RecordSession.stopRuleShort(StopRule.ShotEndPlusTail(5)),
        )
        assertEquals(Res.string.record_stop_short_weight.text(), RecordSession.stopRuleShort(StopRule.WeightSettles(3)))
        assertEquals(Res.string.record_stop_short_fixed.text("30"), RecordSession.stopRuleShort(StopRule.Fixed(30)))
        assertEquals(Res.string.record_stop_short_manual.text(), RecordSession.stopRuleShort(StopRule.Manual))
        assertEquals(Res.string.record_stop_fixed.text("30"), RecordSession.stopRuleText(StopRule.Fixed(30)))
    }

    @Test
    fun aShotWithoutANameIsCalledShotOrTheBrandForAScale() {
        assertEquals(Res.string.record_default_title.text(), RecordSession.defaultProfileName(SourceKind.GAGGIUINO))
        assertEquals("OnlyShots".raw(), RecordSession.defaultProfileName(SourceKind.SCALE))
    }
}
