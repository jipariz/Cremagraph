package app.onlyshots.record

import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopReason
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.heldWrongWay
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.overlay_target_value
import app.onlyshots.core.resources.reading_flow
import app.onlyshots.core.resources.reading_pressure
import app.onlyshots.core.resources.reading_temperature
import app.onlyshots.core.resources.reading_weight
import app.onlyshots.core.resources.record_status_armed
import app.onlyshots.core.resources.record_status_brewing
import app.onlyshots.core.resources.record_status_busy
import app.onlyshots.core.resources.record_status_no_camera
import app.onlyshots.core.resources.record_status_no_machine
import app.onlyshots.core.resources.record_status_pre_roll
import app.onlyshots.core.resources.record_status_shot_ended
import app.onlyshots.core.resources.record_stop_tail
import app.onlyshots.core.resources.record_trigger_gaggiuino
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.unit_bar
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.fake.FakeShotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RecordSessionTest {
    private fun TestScope.session(source: FakeShotSource, settings: RecordingSettings = RecordingSettings()) =
        RecordSession(backgroundScope, source, settings, nowUs = { currentTime * 1000 })

    @Test
    fun armedThenAutoStartThenStopsAfterTail() = runTest {
        val source = FakeShotSource()
        val s = session(source)
        runCurrent()
        assertEquals(RecordPhase.ARMED, s.state.value.phase)
        assertEquals(
            Res.string.record_status_armed.text(Res.string.record_trigger_gaggiuino.text()),
            s.state.value.status,
        )

        launch { source.pull() }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(RecordPhase.RECORDING, s.state.value.phase)
        assertTrue(s.state.value.settingsLocked)
        assertEquals(Res.string.record_status_brewing, (s.state.value.status as UiText.Res).res)
        assertEquals(3_000_000L, s.state.value.shotStartInVideoUs)

        advanceTimeBy(40_500)
        runCurrent()
        assertEquals(
            Res.string.record_status_shot_ended.text(Res.string.record_stop_tail.text("5")),
            s.state.value.status,
        )

        advanceTimeBy(5_000)
        runCurrent()
        val st = s.state.value
        assertEquals(RecordPhase.SAVED, st.phase)
        val finished = assertNotNull(st.finished)
        assertEquals(StopReason.RULE, finished.stopReason)
        assertEquals(3_000_000L, finished.shotStartInVideoUs)
        val zer0 = SampleShots.zer0
        assertEquals(zer0.samples.size, finished.shot!!.samples.size)
        // pre-roll + shot + tail, within one tick
        val expected = 3_000_000L + zer0.durationUs + 5_000_000L
        assertTrue(kotlin.math.abs(finished.durationUs - expected) <= 100_000L, "duration ${finished.durationUs}")
        backgroundScope.coroutineContext.cancelChildren()
    }

    @Test
    fun heldTheWrongWayNothingStartsUntilItIsTurned() = runTest {
        val source = FakeShotSource()
        val s = session(source)
        s.heldWrongWay = true
        runCurrent()
        s.recordPressed()
        assertEquals(RecordPhase.ARMED, s.state.value.phase, "not by hand")
        launch { source.pull() }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(RecordPhase.ARMED, s.state.value.phase, "nor by the machine")
        s.heldWrongWay = false
        s.recordPressed()
        assertEquals(RecordPhase.RECORDING, s.state.value.phase)
        backgroundScope.coroutineContext.cancelChildren()
    }

    @Test
    fun withoutTheCameraNothingStartsUntilItIsAllowed() = runTest {
        val source = FakeShotSource()
        val s = session(source)
        s.noCamera = true
        runCurrent()
        assertTrue(s.state.value.noCamera)
        assertEquals(Res.string.record_status_no_camera.text(), s.state.value.status)
        s.recordPressed()
        assertEquals(RecordPhase.ARMED, s.state.value.phase, "not by hand")
        launch { source.pull() }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(RecordPhase.ARMED, s.state.value.phase, "nor by the machine")
        s.noCamera = false
        assertFalse(s.state.value.noCamera)
        s.recordPressed()
        assertEquals(RecordPhase.RECORDING, s.state.value.phase)
        backgroundScope.coroutineContext.cancelChildren()
    }

    @Test
    fun theScreenStandsTheFormatsWayOrNot() {
        assertFalse(heldWrongWay(1080, 2340, VideoFormat.PORTRAIT_9_16))
        assertTrue(heldWrongWay(1080, 2340, VideoFormat.LANDSCAPE_16_9), "a 16:9 video on an upright screen")
        assertTrue(heldWrongWay(2853, 2007, VideoFormat.SQUARE_1_1), "a foldable's inner display held sideways")
        assertFalse(heldWrongWay(0, 0, VideoFormat.LANDSCAPE_16_9), "a window not measured yet")
    }

    @Test
    fun onItsSideATallFormatRecordsWideForTheTake() {
        assertEquals(
            VideoFormat.LANDSCAPE_16_9,
            takeFormatFor(onItsSide = true, VideoFormat.PORTRAIT_9_16, keepTall = false),
        )
        assertEquals(null, takeFormatFor(onItsSide = true, VideoFormat.PORTRAIT_9_16, keepTall = true), "keep 9:16")
        assertEquals(null, takeFormatFor(onItsSide = false, VideoFormat.PORTRAIT_9_16, keepTall = false))
        assertEquals(
            null,
            takeFormatFor(onItsSide = true, VideoFormat.LANDSCAPE_16_9, keepTall = false),
            "wide already",
        )
    }

    @Test
    fun theTilesFollowTheSource() {
        val machine = takeReadings(Sample(0, pressure = 2.5f, targetWeight = 49f), SourceKind.GAGGIUINO)
        assertEquals(
            listOf(
                Res.string.reading_pressure.text(),
                Res.string.reading_flow.text(),
                UiText.Joined(
                    listOf(
                        Res.string.reading_weight.text(),
                        Res.string.overlay_target_value.text("49.0", Res.string.unit_grams.text()),
                    )
                ),
                Res.string.reading_temperature.text(),
            ),
            machine.map { it.label },
        )
        assertEquals(Res.string.unit_bar, machine.first().unit)
        assertEquals("2.5", machine.first().value)
        assertEquals("–", machine[1].value)
        assertEquals(
            listOf(Res.string.reading_weight.text(), Res.string.reading_flow.text()),
            takeReadings(null, SourceKind.SCALE).map { it.label },
        )
        assertEquals(VideoFormat.LANDSCAPE_16_9, formatThatFits(951, 669))
        assertEquals(VideoFormat.PORTRAIT_9_16, formatThatFits(669, 951))
    }

    @Test
    fun manualRecordingWithoutShot() = runTest {
        val source = FakeShotSource()
        val s = session(source, RecordingSettings(autoStart = false, stopRule = StopRule.Manual))
        runCurrent()
        assertEquals(RecordPhase.MANUAL, s.state.value.phase)
        s.recordPressed()
        advanceTimeBy(2_050)
        runCurrent()
        assertEquals(Res.string.record_status_pre_roll.text(), s.state.value.status)
        s.recordPressed()
        val finished = assertNotNull(s.state.value.finished)
        assertNull(finished.shot)
        assertNull(finished.stopReason)
        assertEquals(2_050_000L, finished.durationUs)
        s.recordPressed() // record another
        assertEquals(RecordPhase.MANUAL, s.state.value.phase)
    }

    @Test
    fun notReachableAndBusyStates() = runTest {
        val source = FakeShotSource()
        source.setConnection(ConnectionState.Busy)
        val s = session(source)
        runCurrent()
        assertEquals(RecordPhase.NOT_REACHABLE, s.state.value.phase)
        assertEquals(Res.string.record_status_busy.text(), s.state.value.status)
        source.setConnection(ConnectionState.Connected)
        runCurrent()
        assertEquals(RecordPhase.ARMED, s.state.value.phase)
    }

    @Test
    fun withoutAMachineThereIsNoData() = runTest {
        val source = FakeShotSource()
        val s = session(source)
        source.setConnection(ConnectionState.Unreachable("wifi"))
        runCurrent()
        assertFalse(s.state.value.hasData)
        assertEquals(Res.string.record_status_no_machine.text(), s.state.value.status)
        source.setConnection(ConnectionState.Connecting)
        runCurrent()
        assertFalse(s.state.value.hasData)
        source.setConnection(ConnectionState.Connected)
        runCurrent()
        assertTrue(s.state.value.hasData)
    }

    @Test
    fun losingConnectionKeepsRecording() = runTest {
        val source = FakeShotSource()
        val s = session(source)
        runCurrent()
        launch { source.pull() }
        advanceTimeBy(10_000)
        runCurrent()
        source.setConnection(ConnectionState.Unreachable("wifi"))
        runCurrent()
        assertEquals(RecordPhase.RECORDING, s.state.value.phase)
        assertTrue(s.state.value.hasData, "the graph already filming stays")
        advanceTimeBy(2_000)
        runCurrent()
        source.setConnection(ConnectionState.Connected)
        advanceTimeBy(40_000)
        runCurrent()
        val shot = assertNotNull(s.state.value.finished?.shot)
        assertTrue(shot.gaps.isNotEmpty(), "gap recorded")
        assertEquals(RecordSession.UNNAMED, shot.profileName, "named when saved, by the view model")
    }

    @Test
    fun safetyStopOnLowStorage() = runTest {
        val source = FakeShotSource()
        var free = Long.MAX_VALUE
        val s =
            RecordSession(
                backgroundScope,
                source,
                RecordingSettings(autoStart = false),
                nowUs = { currentTime * 1000 },
                freeBytes = { free },
            )
        runCurrent()
        s.recordPressed()
        advanceTimeBy(1_000)
        runCurrent()
        free = 10L * 1024 * 1024
        advanceTimeBy(200)
        runCurrent()
        assertEquals(StopReason.LOW_STORAGE, s.state.value.finished?.stopReason)
    }
}
