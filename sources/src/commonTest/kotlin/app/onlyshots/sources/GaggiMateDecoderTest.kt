package app.onlyshots.sources

import app.onlyshots.sources.machine.GaggiMateDecoder
import app.onlyshots.sources.machine.MachineMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Frames in the exact shape `WebSocketHandler.cpp` serializes them (ArduinoJson, field order kept). */
internal object GaggiMateFrames {
    /** The fast frame, every 500 ms: live readings plus the running process. */
    fun telemetry(
        process: String = "null",
        pressure: Float = 6.2f,
        flow: Float = 2.1f,
        weight: Float = 0f,
        temperature: Float = 93.1f,
    ) =
        """{"tp":"evt:status","ct":$temperature,"cst":128.4,"tst":135,"db":false,"tt":93,"pr":$pressure,""" +
            """"fl":$flow,"pt":9,"wl":71,"tof":120,"rssi":-54,"lat":12,"pw":80,"hp":0.4,"rtx":0,"bw":0,""" +
            """"cw":$weight,"process":$process,"pkr":0.4,"pf":1.8,"tf":2,"wp":18.2}"""

    /** The slow frame, sent in full as a client connects and whenever its content changes. */
    fun state(profile: String = "Londinium", mode: Int = 1, hasPressure: Boolean = true) =
        """{"tp":"evt:status","m":$mode,"p":"$profile","cp":$hasPressure,"cd":true,""" +
            """"sys":{"s":"ready","m":"","c":0},"warn":[]}"""

    /** A brew process. [utility] marks a flush, backflush or descaling run. */
    fun brew(
        elapsedMs: Long,
        active: Boolean = true,
        phase: String = "Pre-infusion",
        stage: String = "infusion",
        utility: Boolean = false,
        target: String = "time",
        phaseTarget: Float = 6f,
    ) =
        """{"a":${if (active) 1 else 0},"s":"$stage","l":"$phase","e":$elapsedMs,""" +
            """"u":${if (utility) 1 else 0},"tt":"$target","pt":$phaseTarget,"pp":1200}"""

    const val GRIND = """{"a":1,"s":"grind","l":"Grinding","e":2400,"tt":"time","pt":8000,"pp":2400}"""

    const val OTA = """{"tp":"evt:ota-progress","phase":1,"progress":42}"""
}

class GaggiMateDecoderTest {
    private val d = GaggiMateDecoder()

    private fun List<MachineMessage>.readings() = filterIsInstance<MachineMessage.Reading>().map { it.sample }

    private fun List<MachineMessage>.brews() = filterIsInstance<MachineMessage.Brew>().map { it.event }

    @Test
    fun anythingThatIsNotAStatusEventIsIgnored() {
        val out = d.decodeText(GaggiMateFrames.OTA, receivedAtUs = 1_000_000, "/ws")
        assertEquals(listOf(MachineMessage.Ignored), out)
    }

    @Test
    fun garbageIsIgnored() {
        assertEquals(listOf(MachineMessage.Ignored), d.decodeText("not json", receivedAtUs = 1, "/ws"))
    }

    @Test
    fun anIdleFrameIsAReadingThatIsNotBrewing() {
        val s = d.decodeText(GaggiMateFrames.telemetry(), receivedAtUs = 1_000_000, "/ws").readings().single()
        assertEquals(1_000_000, s.tUs)
        assertEquals(6.2f, s.pressure)
        assertEquals(2.1f, s.flow)
        assertEquals(93.1f, s.temperature)
        assertEquals(93f, s.targetTemperature)
        assertEquals(9f, s.targetPressure)
        assertEquals(2f, s.targetFlow)
        assertTrue(!s.brewing)
    }

    @Test
    fun shotStartIsPlacedAtElapsedZero() {
        // The first brew frame says 300 ms in and arrives at 10.0 s on our clock.
        val out =
            d.decodeText(
                GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(300)),
                receivedAtUs = 10_000_000,
                "/ws",
            )
        assertEquals(listOf<BrewEvent>(BrewEvent.Started(9_700_000)), out.brews())
        val s = out.readings().single()
        assertEquals(10_000_000, s.tUs)
        assertTrue(s.brewing)
        assertEquals(0, s.phase)
    }

    @Test
    fun aPhaseIsNumberedInTheOrderItsNameFirstShows() {
        val phases =
            listOf("Pre-infusion" to 0L, "Pre-infusion" to 500L, "Ramp" to 1000L, "Pour" to 1500L, "Ramp" to 2000L)
                .flatMap { (name, ms) ->
                    d.decodeText(
                            GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(ms, phase = name)),
                            receivedAtUs = 1_000_000 + ms * 1000,
                            "/ws",
                        )
                        .readings()
                }
                .map { it.phase }
        assertEquals(listOf(0, 0, 1, 2, 1), phases)
    }

    @Test
    fun theShotEndsWhenTheProcessGoesInactive() {
        d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(0)), receivedAtUs = 1_000_000, "/ws")
        val out =
            d.decodeText(
                GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(27_400, active = false)),
                receivedAtUs = 40_000_000,
                "/ws",
            )
        // The end is the machine's own frozen elapsed time, not the moment we noticed.
        assertEquals(listOf<BrewEvent>(BrewEvent.Ended(1_000_000 + 27_400_000)), out.brews())
        assertEquals(28_400_000, out.readings().single().tUs)
    }

    @Test
    fun theShotEndsWhenTheProcessGoesAway() {
        d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(0)), receivedAtUs = 1_000_000, "/ws")
        d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(2_000)), receivedAtUs = 3_000_000, "/ws")
        val out = d.decodeText(GaggiMateFrames.telemetry(), receivedAtUs = 3_500_000, "/ws")
        assertEquals(listOf<BrewEvent>(BrewEvent.Ended(3_000_000)), out.brews())
    }

    @Test
    fun theShotEndsWhenTheProcessTurnsIntoAGrind() {
        d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(0)), receivedAtUs = 1_000_000, "/ws")
        val out =
            d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.GRIND), receivedAtUs = 2_000_000, "/ws")
        assertEquals(listOf<BrewEvent>(BrewEvent.Ended(1_000_000)), out.brews())
    }

    @Test
    fun theShotEndsWhenBrewFramesStopArriving() {
        d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(0)), receivedAtUs = 1_000_000, "/ws")
        // A slow frame on its own says nothing about the process, so it changes nothing.
        assertEquals(
            listOf(MachineMessage.Ignored),
            d.decodeText(GaggiMateFrames.state(), receivedAtUs = 1_500_000, "/ws"),
        )
        val out = d.decodeText(GaggiMateFrames.state(), receivedAtUs = 9_000_000, "/ws")
        assertEquals(listOf<BrewEvent>(BrewEvent.Ended(1_000_000)), out.brews())
    }

    @Test
    fun aShotThatRestartsWithoutGoingIdleClosesTheOldOne() {
        d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(5_000)), receivedAtUs = 10_000_000, "/ws")
        val out =
            d.decodeText(
                GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(100)),
                receivedAtUs = 20_000_000,
                "/ws",
            )
        assertEquals(
            listOf<BrewEvent>(BrewEvent.Ended(10_000_000), BrewEvent.Started(19_900_000)),
            out.brews(),
        )
    }

    @Test
    fun aUtilityRunIsNeverAShot() {
        val out =
            d.decodeText(
                GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(4_000, utility = true)),
                receivedAtUs = 1_000_000,
                "/ws",
            )
        assertEquals(emptyList(), out.brews())
        assertTrue(!out.readings().single().brewing)
    }

    @Test
    fun aFinishedProcessLingeringAfterConnectStartsNothing() {
        val out =
            d.decodeText(
                GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(27_400, active = false)),
                receivedAtUs = 1_000_000,
                "/ws",
            )
        assertEquals(emptyList(), out.brews())
        assertTrue(!out.readings().single().brewing)
    }

    @Test
    fun aVolumetricPhaseCarriesItsTargetWeight() {
        val out =
            d.decodeText(
                GaggiMateFrames.telemetry(
                    process = GaggiMateFrames.brew(1_000, target = "volumetric", phaseTarget = 36f)
                ),
                receivedAtUs = 1_000_000,
                "/ws",
            )
        val s = out.readings().single()
        assertEquals(36f, s.targetWeight)
        // The phase's target must never be read as the machine's target pressure.
        assertEquals(9f, s.targetPressure)
    }

    @Test
    fun aMachineWithoutAPressureSensorReportsNoPressureRatherThanZero() {
        d.decodeText(GaggiMateFrames.state(hasPressure = false), receivedAtUs = 1_000_000, "/ws")
        val s =
            d.decodeText(GaggiMateFrames.telemetry(pressure = 0f), receivedAtUs = 1_500_000, "/ws").readings().single()
        assertNull(s.pressure)
        assertNull(s.targetPressure)
        // Everything the Standard does measure still arrives.
        assertEquals(93.1f, s.temperature)
        assertEquals(2.1f, s.flow)
    }

    @Test
    fun aPressureOfZeroFromAProIsAReading() {
        d.decodeText(GaggiMateFrames.state(hasPressure = true), receivedAtUs = 1_000_000, "/ws")
        val s =
            d.decodeText(GaggiMateFrames.telemetry(pressure = 0f), receivedAtUs = 1_500_000, "/ws").readings().single()
        assertEquals(0f, s.pressure)
    }

    @Test
    fun aFastFrameKeepsWhatOnlyTheSlowOneSays() {
        d.decodeText(GaggiMateFrames.state(hasPressure = false), receivedAtUs = 1_000_000, "/ws")
        d.decodeText(GaggiMateFrames.telemetry(), receivedAtUs = 1_500_000, "/ws")
        // Still a Standard three frames later: the fast frame never mentions `cp`.
        assertNull(
            d.decodeText(GaggiMateFrames.telemetry(), receivedAtUs = 2_000_000, "/ws").readings().single().pressure
        )
    }

    @Test
    fun aKeySentAsNullClearsIt() {
        d.decodeText(GaggiMateFrames.telemetry(weight = 18.2f), receivedAtUs = 1_000_000, "/ws")
        assertEquals(
            18.2f,
            d.decodeText(GaggiMateFrames.state(), receivedAtUs = 1_200_000, "/ws").readings().single().weight,
        )
        val cleared = d.decodeText("""{"tp":"evt:status","cw":null}""", receivedAtUs = 1_400_000, "/ws")
        assertNull(cleared.readings().single().weight)
    }

    @Test
    fun resetForgetsTheShotAndTheMergedStatus() {
        d.decodeText(GaggiMateFrames.state(hasPressure = false), receivedAtUs = 1_000_000, "/ws")
        d.decodeText(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(1_000)), receivedAtUs = 2_000_000, "/ws")
        d.reset()
        // No Ended for the shot that was running, and `cp` is unknown again, so pressure is believed.
        val out = d.decodeText(GaggiMateFrames.telemetry(), receivedAtUs = 3_000_000, "/ws")
        assertEquals(emptyList(), out.brews())
        assertEquals(6.2f, out.readings().single().pressure)
    }
}
