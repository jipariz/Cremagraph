package app.onlyshots.sources

import app.onlyshots.core.model.Series
import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.ble.BleDeviceLink
import app.onlyshots.sources.ble.BleProtocol
import app.onlyshots.sources.ble.BleReading
import app.onlyshots.sources.ble.BookooScaleProtocol
import app.onlyshots.sources.ble.ScaleShotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class FakeLink(override val protocol: BleProtocol) : BleDeviceLink {
    override val name = protocol.label
    override val readings = MutableSharedFlow<BleReading>(extraBufferCapacity = 64)
    override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    var tares = 0

    override suspend fun tare() {
        tares++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleShotSourceTest {
    @Test
    fun firstDropsStartTheShot() = runTest {
        var now = 0L
        val scale = FakeLink(BookooScaleProtocol)
        val source = ScaleShotSource(scale, nowUs = { now })
        assertEquals(SourceKind.SCALE, source.kind)
        assertEquals(setOf(Series.WEIGHT, Series.WEIGHT_FLOW), source.capabilities.series)
        val assembler = LiveShotAssembler("s", SourceKind.SCALE, "OnlyShots", Instant.fromEpochSeconds(0))
        backgroundScope.launch { source.brewEvents.collect { assembler.onBrewEvent(it) } }
        backgroundScope.launch { source.live.collect { assembler.onSample(it) } }
        backgroundScope.launch { source.start(this) }
        runCurrent()

        for (i in 0..30) {
            now = i * 100_000L
            // A cup on the scale for a second, then 2 g/s on top of it.
            scale.readings.emit(BleReading(weightG = if (i < 10) 182.1f else 182.1f + (i - 9) * 0.2f))
            runCurrent()
        }
        val shot = assembler.shot()
        assertEquals(0L, shot.samples.first().tUs)
        // The shot starts from rest at the first reading over 0.3 g above the cup (which gives way to 0 g), then
        // the weights measured from the cup.
        assertEquals(20, shot.samples.size)
        assertEquals(0f, shot.samples.first().weight!!, 1e-3f)
        assertEquals(0f, shot.samples.first().weightFlow!!, 1e-3f)
        assertEquals(0.6f, shot.samples[1].weight!!, 1e-3f)
        assertNull(shot.samples.first().pressure)
        assertTrue(shot.samples.last().weightFlow!! > 1.9f, "derived weight flow")
    }

    @Test
    fun tareRearmsTheTrigger() = runTest {
        var now = 0L
        val scale = FakeLink(BookooScaleProtocol)
        val source = ScaleShotSource(scale, nowUs = { now })
        val starts = mutableListOf<BrewEvent>()
        backgroundScope.launch { source.brewEvents.collect { starts += it } }
        backgroundScope.launch { source.start(this) }
        runCurrent()
        suspend fun reading(g: Float) {
            scale.readings.emit(BleReading(weightG = g))
            runCurrent()
            now += 100_000L
        }
        repeat(8) { reading(0f) }
        reading(0.5f)
        reading(1f)
        assertEquals(1, starts.size)
        source.tare()
        assertEquals(1, scale.tares)
        repeat(8) { reading(0f) }
        reading(0.6f)
        reading(1f)
        assertEquals(2, starts.size)
    }

    @Test
    fun gettingReadyForAShotTaresTheScaleEvenThroughTheHolder() = runTest {
        val scale = FakeLink(BookooScaleProtocol)
        val holder = SourceHolder(backgroundScope, ScaleShotSource(scale, nowUs = { 0L }))
        holder.prepareForShot()
        assertEquals(1, scale.tares)
    }

    @Test
    fun liftingTheCupEndsTheShotAtItsLastWeight() = runTest {
        var now = 0L
        val scale = FakeLink(BookooScaleProtocol)
        val source = ScaleShotSource(scale, nowUs = { now })
        val assembler = LiveShotAssembler("s", SourceKind.SCALE, "OnlyShots", Instant.fromEpochSeconds(0))
        backgroundScope.launch { source.brewEvents.collect { assembler.onBrewEvent(it) } }
        backgroundScope.launch { source.live.collect { assembler.onSample(it) } }
        backgroundScope.launch { source.start(this) }
        runCurrent()

        for (i in 0..40) {
            now = i * 100_000L
            // Tared with the cup on, 2 g/s from the second reading on, still dripping when the cup is lifted at 3 s.
            val g = if (i < 10) 0f else if (i < 30) (i - 9) * 0.2f else -118f
            scale.readings.emit(BleReading(weightG = g))
            runCurrent()
        }
        val shot = assembler.shot()
        // The shot ends where the cup came off, at the last weight in it: never 0 g, never the cup's minus.
        assertEquals(assembler.endedAtUs, shot.durationUs)
        assertTrue(shot.samples.all { (it.weight ?: 0f) >= 0f })
        assertEquals(4f, shot.finalWeight!!, 1e-3f)
    }
}
