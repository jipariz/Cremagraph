package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StopRulesTest {
    @Test
    fun aScaleCannotEndOnShotEndAndStopsWhenTheWeightSettlesInstead() {
        assertFalse(StopRule.ShotEndPlusTail().availableFor(SourceKind.SCALE))
        assertTrue(StopRule.ShotEndPlusTail().availableFor(SourceKind.GAGGIUINO))
        for (rule in listOf(StopRule.WeightSettles(), StopRule.Fixed(), StopRule.Manual)) assertTrue(
            rule.availableFor(SourceKind.SCALE),
            "$rule",
        )

        val saved = RecordingSettings(stopRule = StopRule.ShotEndPlusTail(8))
        assertEquals(StopRule.WeightSettles(), saved.effectiveFor(SourceKind.SCALE).stopRule)
        // The saved setting is the machine's: it keeps its shot end and tail.
        assertEquals(saved, saved.effectiveFor(SourceKind.GAGGIUINO))
        val fixed = saved.copy(stopRule = StopRule.Fixed(30))
        assertEquals(fixed, fixed.effectiveFor(SourceKind.SCALE))
    }

    @Test
    fun aScalesGraphAddsWeightFlowToTheDefaultSeriesOnly() {
        val defaults = OverlaySettings()
        assertTrue(Series.WEIGHT_FLOW in defaults.forSource(SourceKind.SCALE).series)
        assertEquals(defaults, defaults.forSource(SourceKind.GAGGIUINO))
        val chosen = defaults.copy(series = setOf(Series.WEIGHT))
        assertEquals(chosen, chosen.forSource(SourceKind.SCALE))
    }

    private val plenty = 10L * 1024 * 1024 * 1024

    private fun sec(v: Double) = (v * 1_000_000).toLong()

    @Test
    fun shotEndPlusTailWaitsForTail() {
        val rule = StopRule.ShotEndPlusTail(tailSeconds = 5)
        assertNull(StopRules.evaluate(rule, ShotProgress(nowUs = sec(34.0)), sec(37.0), plenty))
        assertNull(StopRules.evaluate(rule, ShotProgress(nowUs = sec(38.9), endedAtUs = sec(34.0)), sec(41.9), plenty))
        assertEquals(
            StopReason.RULE,
            StopRules.evaluate(rule, ShotProgress(nowUs = sec(39.0), endedAtUs = sec(34.0)), sec(42.0), plenty),
        )
    }

    @Test
    fun zeroTailStopsAtShotEnd() {
        val rule = StopRule.ShotEndPlusTail(tailSeconds = 0)
        assertEquals(
            StopReason.RULE,
            StopRules.evaluate(rule, ShotProgress(nowUs = sec(30.0), endedAtUs = sec(30.0)), sec(33.0), plenty),
        )
    }

    @Test
    fun fixedRunsOnShotTimeOrRecordingTimeWithoutShot() {
        val rule = StopRule.Fixed(seconds = 45)
        // Shot time 44 s even though 47 s have been recorded including pre-roll.
        assertNull(StopRules.evaluate(rule, ShotProgress(nowUs = sec(44.0)), sec(47.0), plenty))
        assertEquals(StopReason.RULE, StopRules.evaluate(rule, ShotProgress(nowUs = sec(45.0)), sec(48.0), plenty))
        assertEquals(StopReason.RULE, StopRules.evaluate(rule, ShotProgress(nowUs = null), sec(45.0), plenty))
    }

    @Test
    fun manualNeverStopsByRule() {
        assertNull(
            StopRules.evaluate(
                StopRule.Manual,
                ShotProgress(nowUs = sec(100.0), endedAtUs = sec(30.0)),
                sec(103.0),
                plenty,
            )
        )
    }

    @Test
    fun safetyStopsApplyToEveryRule() {
        assertEquals(
            StopReason.MAX_DURATION,
            StopRules.evaluate(StopRule.Manual, ShotProgress(null), StopRules.MAX_RECORDING_US, plenty),
        )
        assertEquals(
            StopReason.LOW_STORAGE,
            StopRules.evaluate(StopRule.Manual, ShotProgress(null), sec(1.0), StopRules.MIN_FREE_BYTES - 1),
        )
        assertNull(
            StopRules.evaluate(
                StopRule.Manual,
                ShotProgress(null),
                StopRules.MAX_RECORDING_US - 1,
                StopRules.MIN_FREE_BYTES,
            )
        )
    }

    @Test
    fun weightSettlesNeedsFirstDropsAndQuietWindow() {
        // 0 g during pre-infusion (settled but no drops), rising 0..36 g from 8 s to 30 s, flat after.
        val samples =
            (0..400).map { i ->
                val t = i * 100_000L
                val w =
                    when {
                        t < sec(8.0) -> 0f
                        t < sec(30.0) -> (t - sec(8.0)) / 1_000_000f * (36f / 22f)
                        else -> 36f + if (i % 2 == 0) 0.04f else 0f
                    }
                Sample(tUs = t, weight = w)
            }
        val rule = StopRule.WeightSettles(seconds = 3)
        fun at(now: Double) =
            StopRules.evaluate(
                rule,
                ShotProgress(nowUs = sec(now), samples = samples.filter { it.tUs <= sec(now) }),
                sec(now + 3),
                plenty,
            )
        assertNull(at(7.0)) // only zeros so far
        assertNull(at(20.0)) // still rising
        assertNull(at(32.9)) // flat for 2.9 s
        assertEquals(StopReason.RULE, at(33.1))
    }

    @Test
    fun rangesAreEnforced() {
        assertFailsWith<IllegalArgumentException> { StopRule.ShotEndPlusTail(31) }
        assertFailsWith<IllegalArgumentException> { StopRule.WeightSettles(0) }
        assertFailsWith<IllegalArgumentException> { StopRule.Fixed(14) }
        assertFailsWith<IllegalArgumentException> { RecordingSettings(preRollSeconds = 11) }
    }

    @Test
    fun defaultsMatchTheSpec() {
        val d = RecordingSettings()
        assertEquals(3, d.preRollSeconds)
        assertEquals(StopRule.ShotEndPlusTail(5), d.stopRule)
        assertEquals(true, d.autoStart)
    }

    @Test
    fun steppingARuleStaysInsideItsRange() {
        // The constructors require their ranges, so a stepper that did not clamp would throw.
        assertEquals(StopRule.ShotEndPlusTail(6), StopRule.ShotEndPlusTail(5).stepped(1))
        assertEquals(StopRule.ShotEndPlusTail(0), StopRule.ShotEndPlusTail(0).stepped(-1))
        assertEquals(StopRule.ShotEndPlusTail(30), StopRule.ShotEndPlusTail(30).stepped(1))

        assertEquals(StopRule.WeightSettles(1), StopRule.WeightSettles(1).stepped(-1))
        assertEquals(StopRule.WeightSettles(10), StopRule.WeightSettles(10).stepped(5))

        assertEquals(StopRule.Fixed(15), StopRule.Fixed(15).stepped(-10))
        assertEquals(StopRule.Fixed(180), StopRule.Fixed(180).stepped(1))

        assertEquals(StopRule.Manual, StopRule.Manual.stepped(1))
    }

    @Test
    fun onlyTheRulesWithAParameterShowOne() {
        assertEquals("5s", StopRule.ShotEndPlusTail(5).stepLabel)
        assertEquals("3s", StopRule.WeightSettles(3).stepLabel)
        assertEquals("45s", StopRule.Fixed(45).stepLabel)
        assertNull(StopRule.Manual.stepLabel)
    }

    @Test
    fun weightSettlesStopsAtOnceWhenTheCupComesOff() {
        // Still dripping when the cup is lifted at 31 s: nothing has settled, but the shot is over.
        val samples = (0..31).map { Sample(tUs = sec(it.toDouble()), weight = it * 1.2f, brewing = true) }
        val rule = StopRule.WeightSettles(seconds = 3)
        assertNull(StopRules.evaluate(rule, ShotProgress(nowUs = sec(30.9), samples = samples), sec(33.9), plenty))
        assertEquals(
            StopReason.RULE,
            StopRules.evaluate(
                rule,
                ShotProgress(nowUs = sec(31.0), endedAtUs = sec(31.0), samples = samples),
                sec(34.0),
                plenty,
            ),
        )
    }
}
