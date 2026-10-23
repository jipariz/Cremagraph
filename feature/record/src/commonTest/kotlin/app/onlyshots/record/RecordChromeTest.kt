package app.onlyshots.record

import androidx.compose.ui.geometry.Rect
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.component.RecordButtonState
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Series
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.resources.raw
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.WindowLayout
import app.onlyshots.sources.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordChromeTest {
    private fun take(
        phase: RecordPhase = RecordPhase.ARMED,
        connection: ConnectionState = ConnectionState.Connected,
        noCamera: Boolean = false,
        samples: List<Sample> = SampleShots.zer0.samples.take(3),
    ) =
        RecordUiState(
            phase = phase,
            status = "".raw(),
            connection = connection,
            sourceKind = SourceKind.GAGGIUINO,
            shot = SampleShots.zer0.copy(samples = samples),
            stopRuleText = "".raw(),
            noCamera = noCamera,
        )

    private val offline = ConnectionState.Unreachable("gone")

    @Test
    fun theFloatingControlsGoWhereTheWindowPutsThem() {
        val portrait = VideoFormat.PORTRAIT_9_16
        val landscape = VideoFormat.LANDSCAPE_16_9
        val book = WindowLayout(LayoutKind.Wide, splitDp = 400f, hingeDp = 20f, fold = true)
        assertEquals(ControlsPlacement.Fold, book.controlsPlacement(landscape))
        assertEquals(ControlsPlacement.Landscape, WindowLayout(LayoutKind.Wide).controlsPlacement(landscape))
        assertEquals(ControlsPlacement.Landscape, WindowLayout(LayoutKind.Phone).controlsPlacement(landscape))
        assertEquals(ControlsPlacement.TabletUpright, WindowLayout(LayoutKind.Wide).controlsPlacement(portrait))
        assertEquals(ControlsPlacement.PhoneUpright, WindowLayout(LayoutKind.Phone).controlsPlacement(portrait))
        // A fold that is not a book's open pages (a tabletop) is no fold for the floating controls.
        val tabletop = WindowLayout(LayoutKind.Stacked, splitDp = 400f, fold = true)
        assertEquals(ControlsPlacement.PhoneUpright, tabletop.controlsPlacement(portrait))
    }

    @Test
    fun onABookTheSheetsFillTheRightPage() {
        assertEquals(420f, WindowLayout(LayoutKind.Wide, splitDp = 400f, hingeDp = 20f, fold = true).foldPageStartDp)
        assertNull(WindowLayout(LayoutKind.Wide, splitDp = 400f).foldPageStartDp)
        assertNull(WindowLayout(LayoutKind.Stacked, splitDp = 400f, fold = true).foldPageStartDp)
    }

    @Test
    fun recordingOrLosingTheDataEndsEditingTheGraph() {
        assertFalse(take().endsGraphEditing)
        assertTrue(take(phase = RecordPhase.RECORDING).endsGraphEditing)
        assertTrue(take(phase = RecordPhase.NOT_REACHABLE, connection = offline).endsGraphEditing)
    }

    @Test
    fun theGraphShowsWithDataAndAPictureButNotOnACoverScreen() {
        val armed = take()
        assertTrue(armed.showsGraph(granted = true, PaneChrome.Floating))
        assertTrue(armed.showsGraph(granted = true, PaneChrome.Own))
        assertFalse(armed.showsGraph(granted = true, PaneChrome.Bare))
        assertFalse(armed.showsGraph(granted = false, PaneChrome.Floating))
        assertFalse(take(connection = offline).showsGraph(granted = true, PaneChrome.Floating))
        // Placed by hand before the shot only.
        assertTrue(armed.editsGraph(granted = true, PaneChrome.Floating))
        val recording = take(phase = RecordPhase.RECORDING)
        assertTrue(recording.showsGraph(granted = true, PaneChrome.Floating))
        assertFalse(recording.editsGraph(granted = true, PaneChrome.Floating))
    }

    @Test
    fun focusLockedShowsUnderAHeldRingBeforeTheTake() {
        val held = CameraState(focusLocked = true)
        assertTrue(take().showsFocusLock(held, ringShown = true, granted = true, PaneChrome.Floating))
        assertFalse(take().showsFocusLock(CameraState(), ringShown = true, granted = true, PaneChrome.Floating))
        assertFalse(take().showsFocusLock(held, ringShown = false, granted = true, PaneChrome.Floating))
        assertFalse(take().showsFocusLock(held, ringShown = true, granted = true, PaneChrome.Bare))
        val recording = take(phase = RecordPhase.RECORDING)
        assertFalse(recording.showsFocusLock(held, ringShown = true, granted = true, PaneChrome.Floating))
    }

    @Test
    fun theLensesHideMidTakeAndWithoutACamera() {
        assertTrue(take().showsLenses(granted = true))
        assertFalse(take().showsLenses(granted = false))
        assertFalse(take(phase = RecordPhase.RECORDING).showsLenses(granted = true))
    }

    @Test
    fun theStatusKeepsOutOfTheGraphsWay() {
        assertTrue(graphOnTop(NormRect(0.1f, 0.1f, 0.8f, 0.2f)))
        assertFalse(graphOnTop(NormRect(0.1f, 0.4f, 0.8f, 0.3f)))
        // The chip goes to the opposite corner: the bottom while the graph starts in the upper half.
        assertEquals(1f, statusChipBias(NormRect(0.1f, 0.3f, 0.8f, 0.3f)))
        assertEquals(-1f, statusChipBias(NormRect(0.1f, 0.6f, 0.8f, 0.3f)))
    }

    @Test
    fun theLiveGraphDrawsOnlyWhatTheSourceCanSend() {
        val rect = NormRect(0.1f, 0.1f, 0.5f, 0.2f)
        val machine = liveOverlay(OverlaySettings(), SourceKind.GAGGIUINO, rect)
        assertEquals(rect, machine.rect)
        assertEquals(OverlaySettings.DefaultSeries, machine.series)
        // A scale has no pressure or flow; the targets stay, and weight flow joins the default set.
        val scale = liveOverlay(OverlaySettings(), SourceKind.SCALE, rect)
        assertFalse(Series.PRESSURE in scale.series)
        assertFalse(Series.FLOW in scale.series)
        assertTrue(Series.WEIGHT in scale.series)
        assertTrue(Series.TARGETS in scale.series)
    }

    @Test
    fun theTitleNamesTheProfileOrItsStandIn() {
        assertEquals("Zer0 · Gaggiuino", graphTitle("Zer0", "espresso", "Gaggiuino"))
        assertEquals("espresso · Gaggiuino", graphTitle("", "espresso", "Gaggiuino"))
    }

    @Test
    fun theStatusSaysOnlyWhatTheScreenDoesNot() {
        assertTrue(take().statusWorthShowing)
        assertFalse(take(phase = RecordPhase.NOT_REACHABLE, connection = offline).statusWorthShowing)
        assertTrue(take(phase = RecordPhase.NOT_REACHABLE, connection = offline, noCamera = true).statusWorthShowing)
    }

    @Test
    fun theButtonFollowsThePhase() {
        assertEquals(RecordButtonState.Recording, take(phase = RecordPhase.RECORDING).buttonState())
        assertEquals(RecordButtonState.Disabled, take(noCamera = true).buttonState())
        assertEquals(RecordButtonState.Armed, take().buttonState())
        // Not armed (no machine, or by hand): idle, on the cover screen too, which used to show it armed.
        assertEquals(RecordButtonState.Idle, take(phase = RecordPhase.MANUAL).buttonState())
        assertEquals(
            RecordButtonState.Idle,
            take(phase = RecordPhase.NOT_REACHABLE, connection = offline).buttonState(),
        )
    }

    @Test
    fun theRulesBlockFollowsTheTake() {
        assertEquals(RulesMode.RUNNING, take(phase = RecordPhase.RECORDING).rulesMode())
        assertEquals(RulesMode.RUNNING, take(phase = RecordPhase.RECORDING, noCamera = true).rulesMode())
        assertNull(take(noCamera = true).rulesMode())
        assertEquals(RulesMode.RULES, take().rulesMode())
        assertEquals(RulesMode.HINT, take(phase = RecordPhase.NOT_REACHABLE, connection = offline).rulesMode())
    }

    private fun inputs(state: RecordUiState, canSimulateShot: Boolean = true, live: Sample? = null) =
        TakeInputs(
            state = state,
            camera = CameraState(),
            settings = RecordingSettings(),
            live = { live },
            actions = RecordActions.None,
            canSimulateShot = canSimulateShot,
            saveError = null,
            onBack = {},
            onSettings = {},
        )

    @Test
    fun aShotCanBeSimulatedOnlyArmedWithTheReplayAndACamera() {
        assertTrue(inputs(take()).canSimulate)
        assertFalse(inputs(take(), canSimulateShot = false).canSimulate)
        assertFalse(inputs(take(phase = RecordPhase.RECORDING)).canSimulate)
        assertFalse(inputs(take(noCamera = true)).canSimulate)
    }

    @Test
    fun theReadingIsTheShotsWhileRecordingAndTheSourcesBefore() {
        val live = SampleShots.zer0.samples[10]
        val recording = take(phase = RecordPhase.RECORDING)
        assertSame(recording.shot.samples.last(), inputs(recording, live = live).sample)
        assertSame(live, inputs(take(phase = RecordPhase.RECORDING, samples = emptyList()), live = live).sample)
        assertSame(live, inputs(take(), live = live).sample)
    }

    @Test
    fun theControlsLeaveTheWindowBetweenTheTopRowAndTheFoot() {
        val rects = ControlRects()
        assertNull(rects.free(ControlsPlacement.PhoneUpright))
        rects[ControlSlot.Window] = Rect(0f, 0f, 400f, 800f)
        assertEquals(Rect(0f, 0f, 400f, 800f), rects.free(ControlsPlacement.PhoneUpright))
        rects[ControlSlot.Top] = Rect(0f, 20f, 400f, 80f)
        rects[ControlSlot.Bottom] = Rect(0f, 650f, 400f, 780f)
        assertEquals(Rect(0f, 80f, 400f, 650f), rects.free(ControlsPlacement.PhoneUpright))
        // On its side the key's column down the trailing edge is left out too; upright it does not count.
        rects[ControlSlot.Side] = Rect(320f, 300f, 390f, 500f)
        assertEquals(Rect(0f, 80f, 320f, 650f), rects.free(ControlsPlacement.Landscape))
        assertEquals(Rect(0f, 80f, 400f, 650f), rects.free(ControlsPlacement.TabletUpright))
    }

    @Test
    fun onAFoldTheGraphKeepsToTheRightPage() {
        val rects = ControlRects()
        rects[ControlSlot.Window] = Rect(0f, 0f, 800f, 600f)
        rects[ControlSlot.Top] = Rect(0f, 0f, 380f, 60f)
        // Before the page is measured, the window as elsewhere.
        assertEquals(Rect(0f, 60f, 800f, 600f), rects.free(ControlsPlacement.Fold))
        rects[ControlSlot.Page] = Rect(420f, 0f, 800f, 600f)
        assertEquals(Rect(420f, 0f, 800f, 600f), rects.free(ControlsPlacement.Fold))
        rects[ControlSlot.Bottom] = Rect(420f, 450f, 800f, 580f)
        assertEquals(Rect(420f, 0f, 800f, 450f), rects.free(ControlsPlacement.Fold))
    }

    @Test
    fun whatIsFreeIsPassedOnOnlyWhenItChanges() {
        val rects = ControlRects()
        assertNull(rects.changed(ControlsPlacement.PhoneUpright))
        rects[ControlSlot.Window] = Rect(0f, 0f, 400f, 800f)
        assertEquals(Rect(0f, 0f, 400f, 800f), rects.changed(ControlsPlacement.PhoneUpright))
        assertNull(rects.changed(ControlsPlacement.PhoneUpright))
        rects[ControlSlot.Bottom] = Rect(0f, 700f, 400f, 800f)
        assertEquals(Rect(0f, 0f, 400f, 700f), rects.changed(ControlsPlacement.PhoneUpright))
    }
}
