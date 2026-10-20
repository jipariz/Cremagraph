package app.onlyshots.sources

import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LiveShotAssemblerTest {
    private fun assembler() = LiveShotAssembler("id", SourceKind.GAGGIUINO, "Zer0", Instant.fromEpochSeconds(0))

    private fun raw(t: Long, w: Float = 0f, phase: Int? = 1) = Sample(tUs = t, pressure = 1f, weight = w, phase = phase)

    @Test
    fun convertsSourceTimeToShotTime() {
        val a = assembler()
        a.onSample(raw(5_000_000)) // before brew start: dropped
        a.onBrewEvent(BrewEvent.Started(10_000_000))
        a.onSample(raw(9_900_000)) // still before start
        a.onSample(raw(10_000_000))
        a.onSample(raw(10_200_000))
        val shot = a.shot()
        assertEquals(listOf(0L, 200_000L), shot.samples.map { it.tUs })
        assertEquals(200_000L, a.progress().nowUs)
    }

    @Test
    fun samplesArrivingBeforeTheStartEventAreKept() {
        val a = assembler()
        a.onSample(raw(1_000_000)) // idle, before the shot
        a.onSample(raw(10_000_000))
        a.onSample(raw(10_100_000))
        a.onBrewEvent(BrewEvent.Started(10_000_000)) // delivered late on its own flow
        a.onSample(raw(10_200_000))
        assertEquals(listOf(0L, 100_000L, 200_000L), a.shot().samples.map { it.tUs })
    }

    @Test
    fun lateEndEventTrimsSamplesPastTheEnd() {
        val a = assembler()
        a.onBrewEvent(BrewEvent.Started(0))
        a.onSample(raw(1_000_000))
        a.onSample(raw(2_000_000))
        a.onSample(raw(2_500_000)) // arrived before the end event that says the shot ended at 2.0 s
        a.onBrewEvent(BrewEvent.Ended(2_000_000))
        assertEquals(listOf(1_000_000L, 2_000_000L), a.shot().samples.map { it.tUs })
    }

    @Test
    fun idleReadingsDuringTheShotAreIgnored() {
        val a = assembler()
        a.onBrewEvent(BrewEvent.Started(0))
        a.onSample(raw(1_000_000))
        a.onSample(raw(1_500_000).copy(brewing = false))
        assertEquals(1, a.shot().samples.size)
    }

    @Test
    fun recordsShotEndAndPhases() {
        val a = assembler()
        a.onBrewEvent(BrewEvent.Started(1_000))
        a.onSample(raw(1_000, phase = 1))
        a.onSample(raw(501_000, phase = 1))
        a.onSample(raw(1_001_000, phase = 2))
        a.onBrewEvent(BrewEvent.Ended(2_001_000))
        val shot = a.shot()
        assertEquals(listOf(0L, 1_000_000L), shot.phaseStartsUs)
        assertEquals(2_000_000L, shot.durationUs)
        assertEquals(2_000_000L, a.progress().endedAtUs)
    }

    @Test
    fun lostConnectionBecomesAGap() {
        val a = assembler()
        a.onBrewEvent(BrewEvent.Started(0))
        a.onSample(raw(0))
        a.onSample(raw(1_000_000))
        a.onConnectionLost()
        a.onTick(2_500_000)
        assertEquals(2_500_000L, a.progress().nowUs)
        assertEquals(listOf(TimeRange(1_000_000, 2_500_000)), a.shot().gaps) // still open: ends at now
        a.onSample(raw(3_000_000))
        assertEquals(listOf(TimeRange(1_000_000, 3_000_000)), a.shot().gaps)
    }

    @Test
    fun ignoresOutOfOrderSamples() {
        val a = assembler()
        a.onBrewEvent(BrewEvent.Started(0))
        a.onSample(raw(1_000_000))
        a.onSample(raw(900_000))
        assertEquals(1, a.shot().samples.size)
    }

    @Test
    fun derivesWeightFlowAndFinalWeight() {
        val a = assembler()
        a.onBrewEvent(BrewEvent.Started(0))
        for (i in 0..10) a.onSample(raw(i * 100_000L, w = i * 0.3f))
        val shot = a.shot()
        assertEquals(3f, shot.finalWeight!!, 1e-4f)
        assertEquals(3f, shot.samples.last().weightFlow!!, 1e-3f)
    }

    private fun scale() = LiveShotAssembler("id", SourceKind.SCALE, "Scale", Instant.fromEpochSeconds(0))

    private fun drops(t: Long, w: Float) = Sample(tUs = t, weight = w, weightFlow = 1.3f, brewing = true)

    @Test
    fun aScaleShotStartsFromRest() {
        val a = scale()
        a.onBrewEvent(BrewEvent.Started(10_000_000))
        a.onSample(drops(10_000_000, 2.2f)) // the first reading over the trigger, at the very start
        a.onSample(drops(10_100_000, 3.4f))
        val samples = a.shot().samples
        assertEquals(listOf(0L, 100_000L), samples.map { it.tUs })
        assertEquals(0f, samples.first().weight)
        assertEquals(0f, samples.first().weightFlow)
        assertEquals(3.4f, samples[1].weight)
    }

    @Test
    fun aScaleShotStartsFromRestWhenItsReadingsCameFirst() {
        val a = scale()
        a.onSample(drops(10_000_000, 2.2f))
        a.onSample(drops(10_100_000, 3.4f))
        a.onBrewEvent(BrewEvent.Started(10_000_000)) // delivered late on its own flow
        val samples = a.shot().samples
        assertEquals(listOf(0L, 100_000L), samples.map { it.tUs })
        assertEquals(0f, samples.first().weight)
    }

    @Test
    fun aMachineShotKeepsItsFirstReading() {
        val a = assembler()
        a.onBrewEvent(BrewEvent.Started(0))
        a.onSample(raw(0, w = 0.4f))
        assertEquals(0.4f, a.shot().samples.first().weight)
    }
}
