package app.onlyshots.sources

import app.onlyshots.core.model.ShotProgress
import app.onlyshots.core.model.StopReason
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.model.StopRules
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.sources.fake.FakeShotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Auto-start and auto-stop end to end against the replayed Zer0 shot, without a machine. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeSourceRecordingTest {
    @Test
    fun replayedShotIsReassembledInShotTime() = runTest {
        val source = FakeShotSource()
        val assembler = LiveShotAssembler("rec", source.kind, "Zer0", Instant.fromEpochSeconds(0))
        val jobs =
            listOf(
                launch { source.brewEvents.collect { assembler.onBrewEvent(it) } },
                launch { source.live.collect { assembler.onSample(it) } },
            )
        runCurrent()
        source.pull()
        runCurrent()
        val shot = assembler.shot()
        val original = SampleShots.zer0
        assertEquals(original.samples.size, shot.samples.size)
        assertEquals(original.samples.map { it.tUs }, shot.samples.map { it.tUs })
        assertEquals(original.durationUs, shot.durationUs)
        assertEquals(original.phaseStartsUs, shot.phaseStartsUs)
        jobs.forEach { it.cancel() }
    }

    @Test
    fun shotEndRuleStopsFiveSecondsAfterBrewEnd() = runTest {
        val source = FakeShotSource()
        val assembler = LiveShotAssembler("rec", source.kind, "Zer0", Instant.fromEpochSeconds(0))
        val preRollUs = 3_000_000L
        val rule = StopRule.ShotEndPlusTail(5)
        var stoppedAtShotUs: Long? = null
        val startMs = currentTime
        val jobs =
            listOf(
                launch { source.brewEvents.collect { assembler.onBrewEvent(it) } },
                launch { source.live.collect { assembler.onSample(it) } },
                launch { recorderTick(assembler, rule, preRollUs, startMs) { stoppedAtShotUs = it } },
            )
        runCurrent()
        source.pull()
        advanceUntilIdle()
        assertNotNull(stoppedAtShotUs)
        val expected = SampleShots.zer0.durationUs + 5_000_000L
        assertTrue(
            kotlin.math.abs(stoppedAtShotUs - expected) <= 100_000L,
            "stopped at $stoppedAtShotUs, expected $expected",
        )
        jobs.forEach { it.cancel() }
    }

    /** Stand-in for the recorder's 100 ms evaluation tick on the source clock. */
    private suspend fun TestScope.recorderTick(
        assembler: LiveShotAssembler,
        rule: StopRule,
        preRollUs: Long,
        startMs: Long,
        onStop: (Long) -> Unit,
    ) {
        while (true) {
            kotlinx.coroutines.delay(100)
            val elapsedUs = (currentTime - startMs) * 1000
            assembler.onTick(1_000_000_000L + elapsedUs)
            val progress: ShotProgress = assembler.progress()
            val reason =
                StopRules.evaluate(
                    rule,
                    progress,
                    recordingElapsedUs = preRollUs + elapsedUs,
                    freeBytes = Long.MAX_VALUE,
                )
            if (reason == StopReason.RULE) {
                onStop(progress.nowUs!!)
                return
            }
        }
    }
}
