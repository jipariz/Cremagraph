package app.onlyshots.sources

import app.onlyshots.sources.machine.GaggiuinoDecoder
import app.onlyshots.sources.machine.MachineMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Frames in the exact shape `websocket.cpp` serializes them (ArduinoJson, field order kept). */
internal object GaggiuinoFrames {
    fun shot(timeMs: Long, pressure: Float = 2.5f, flow: Float = 1.6f, weight: Float = 0f) =
        """{"action":"shot_data_update","data":{"timeInShot":$timeMs,"pressure":$pressure,"pumpFlow":$flow,""" +
            """"weightFlow":0.4,"temperature":93.1,"shotWeight":$weight,"waterPumped":12.5,""" +
            """"targetTemperature":93,"targetPumpFlow":1.5,"targetPressure":0}}"""

    fun sensor(brewActive: Boolean, weight: Float = 0.1f) =
        """{"action":"sensor_data_update","data":{"brewActive":$brewActive,"steamActive":false,"scalesPresent":true,""" +
            """"temperature":92.8,"waterLvl":71,"pressure":0.2,"pumpFlow":0,"weightFlow":0,"weight":$weight}}"""

    const val LOG = """{"action":"log_record","data":{"source":"webserver","log":"WebSocket client #1 connected"}}"""
}

class GaggiuinoDecoderTest {
    private val d = GaggiuinoDecoder()

    private fun List<MachineMessage>.readings() = filterIsInstance<MachineMessage.Reading>().map { it.sample }

    private fun List<MachineMessage>.brews() = filterIsInstance<MachineMessage.Brew>().map { it.event }

    @Test
    fun shotStartIsPlacedAtTimeInShotZero() {
        // The first frame we see says 300 ms into the shot and arrives at 10.0 s on our clock.
        val out = d.decodeText(GaggiuinoFrames.shot(300), receivedAtUs = 10_000_000, "/ws")
        assertEquals(listOf<BrewEvent>(BrewEvent.Started(9_700_000)), out.brews())
        val s = out.readings().single()
        assertEquals(10_000_000, s.tUs)
        assertEquals(2.5f, s.pressure)
        assertEquals(1.6f, s.flow)
        assertEquals(1.5f, s.targetFlow)
        assertEquals(0f, s.targetPressure)
        assertEquals(93f, s.targetTemperature)
        assertTrue(s.brewing)
    }

    @Test
    fun laterFramesUseTimeInShotNotArrivalTime() {
        d.decodeText(GaggiuinoFrames.shot(0), 5_000_000, "/ws")
        // Arrives late (Wi-Fi jitter) but still lands at 5.1 s.
        val s = d.decodeText(GaggiuinoFrames.shot(100), 5_400_000, "/ws").readings().single()
        assertEquals(5_100_000, s.tUs)
    }

    @Test
    fun shotWeightIsTheWeightOfTheShot() {
        d.decodeText(GaggiuinoFrames.shot(0), 0, "/ws")
        assertEquals(
            12.3f,
            d.decodeText(GaggiuinoFrames.shot(100, weight = 12.3f), 100_000, "/ws").readings().single().weight,
        )
    }

    @Test
    fun endsWhenBrewSwitchGoesOffAtLastTimeInShot() {
        d.decodeText(GaggiuinoFrames.shot(0), 1_000_000, "/ws")
        d.decodeText(GaggiuinoFrames.shot(34_200), 35_200_000, "/ws")
        val out = d.decodeText(GaggiuinoFrames.sensor(brewActive = false), 35_900_000, "/ws")
        assertEquals(listOf<BrewEvent>(BrewEvent.Ended(35_200_000)), out.brews())
    }

    @Test
    fun endsWhenShotFramesStopEvenWithSwitchOn() {
        // The profile finished: the firmware stops shot frames, the switch is still on.
        d.decodeText(GaggiuinoFrames.shot(0), 0, "/ws")
        d.decodeText(GaggiuinoFrames.shot(30_000), 30_000_000, "/ws")
        assertTrue(d.decodeText(GaggiuinoFrames.sensor(brewActive = true), 31_000_000, "/ws").brews().isEmpty())
        assertEquals(
            listOf<BrewEvent>(BrewEvent.Ended(30_000_000)),
            d.decodeText(GaggiuinoFrames.sensor(brewActive = true), 32_000_000, "/ws").brews(),
        )
    }

    @Test
    fun timeJumpingBackStartsANewShot() {
        d.decodeText(GaggiuinoFrames.shot(0), 0, "/ws")
        d.decodeText(GaggiuinoFrames.shot(20_000), 20_000_000, "/ws")
        val out = d.decodeText(GaggiuinoFrames.shot(100), 60_000_000, "/ws")
        assertEquals(listOf(BrewEvent.Ended(20_000_000), BrewEvent.Started(59_900_000)), out.brews())
    }

    @Test
    fun idleSensorReadingsAreNotBrewing() {
        val s =
            d.decodeText(GaggiuinoFrames.sensor(brewActive = false, weight = 0.4f), 3_000_000, "/ws")
                .readings()
                .single()
        assertEquals(false, s.brewing)
        assertEquals(0.4f, s.weight)
        assertNull(s.targetFlow)
    }

    @Test
    fun sensorFramesDuringAShotAreNotMixedIn() {
        d.decodeText(GaggiuinoFrames.shot(0), 0, "/ws")
        assertTrue(d.decodeText(GaggiuinoFrames.sensor(brewActive = true), 500_000, "/ws").readings().isEmpty())
    }

    @Test
    fun ignoresLogsGarbageAndNulls() {
        assertIs<MachineMessage.Ignored>(d.decodeText(GaggiuinoFrames.LOG, 0, "/ws").single())
        assertIs<MachineMessage.Ignored>(d.decodeText("not json", 0, "/ws").single())
        // ArduinoJson writes NaN as null.
        val s =
            d.decodeText(GaggiuinoFrames.shot(0).replace("\"pressure\":2.5", "\"pressure\":null"), 0, "/ws")
                .readings()
                .single()
        assertNull(s.pressure)
    }

    @Test
    fun resetDropsAShotFromADeadConnection() {
        d.decodeText(GaggiuinoFrames.shot(5_000), 5_000_000, "/ws")
        d.reset()
        assertEquals(
            listOf<BrewEvent>(BrewEvent.Started(10_000_000)),
            d.decodeText(GaggiuinoFrames.shot(0), 10_000_000, "/ws").brews(),
        )
    }

    @Test
    fun assemblerDropsIdleReadingsAfterTheShot() {
        val a =
            LiveShotAssembler(
                "x",
                app.onlyshots.core.model.SourceKind.GAGGIUINO,
                "p",
                kotlin.time.Instant.fromEpochSeconds(0),
            )
        fun feed(text: String, at: Long) =
            d.decodeText(text, at, "/ws").forEach {
                when (it) {
                    is MachineMessage.Reading -> a.onSample(it.sample)
                    is MachineMessage.Brew -> a.onBrewEvent(it.event)
                    else -> Unit
                }
            }
        feed(GaggiuinoFrames.sensor(false), 0)
        for (i in 0..20) feed(GaggiuinoFrames.shot(i * 100L), 1_000_000 + i * 100_000L)
        feed(GaggiuinoFrames.sensor(false), 3_200_000)
        feed(GaggiuinoFrames.sensor(false), 4_200_000)
        val shot = a.shot()
        assertEquals(21, shot.samples.size)
        assertEquals(2_000_000, shot.durationUs)
        assertEquals(0L, shot.samples.first().tUs)
    }
}
