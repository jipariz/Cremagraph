package app.onlyshots.edit

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.edit_export
import app.onlyshots.core.resources.edit_making_video
import app.onlyshots.core.resources.edit_phases_count
import app.onlyshots.core.resources.edit_recorded_with_video
import app.onlyshots.core.resources.edit_saved
import app.onlyshots.core.resources.edit_style
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.text
import app.onlyshots.layout.LayoutKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EditChromeTest {
    private val video =
        VideoItem(
            id = "v",
            file = "take.mp4",
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = 30_000_000,
            origin = Origin.IMPORTED,
            hasAudio = true,
            shot = null,
            shotStartInVideoUs = null,
            syncLocked = false,
            trimInUs = 0,
            trimOutUs = 30_000_000,
            overlay = OverlaySettings(),
        )
    private val withShot = video.copy(shot = SampleShots.zer0)

    private fun chrome(
        kind: LayoutKind,
        ui: EditUiState = EditUiState(item = video),
        muted: Boolean = false,
        selected: Boolean = false,
        reframing: Boolean = false,
    ) = editChrome(kind, ui, muted, selected, reframing)

    @Test
    fun theLayoutIsTheWindowsOnceTheVideoIsInAndTinyAlways() {
        assertEquals(LayoutKind.Wide, chrome(LayoutKind.Wide).kind)
        assertEquals(LayoutKind.Stacked, chrome(LayoutKind.Stacked).kind)
        assertEquals(LayoutKind.Phone, chrome(LayoutKind.Wide, EditUiState()).kind)
        assertEquals(LayoutKind.Phone, chrome(LayoutKind.Stacked, EditUiState()).kind)
        assertEquals(LayoutKind.Tiny, chrome(LayoutKind.Tiny, EditUiState()).kind)
    }

    @Test
    fun theWidePanelHoldsTheGraphOnlyWhileAGraphIsSelectedAndNotFramed() {
        val ui = EditUiState(item = withShot)
        assertTrue(chrome(LayoutKind.Wide, ui, selected = true).graphPanel)
        assertFalse(chrome(LayoutKind.Wide, ui, selected = true, reframing = true).graphPanel)
        assertFalse(chrome(LayoutKind.Wide, ui).graphPanel)
        assertFalse(chrome(LayoutKind.Wide, EditUiState(item = video), selected = true).graphPanel)
    }

    @Test
    fun onAWideWindowTheExportCoversTheEditorUntilEitherResultIsSeen() {
        assertTrue(chrome(LayoutKind.Wide, EditUiState(item = video, exporting = 0.3f)).exportOverlay)
        assertTrue(chrome(LayoutKind.Wide, EditUiState(item = video, exported = true)).exportOverlay)
        assertTrue(chrome(LayoutKind.Wide, EditUiState(item = video, savedToGallery = true)).exportOverlay)
        assertFalse(chrome(LayoutKind.Wide).exportOverlay)
    }

    @Test
    fun onAPhoneAndATabletopTheExportCoversTheEditorUntilASavedVideoIsSeen() {
        for (kind in listOf(LayoutKind.Phone, LayoutKind.Stacked)) {
            val running = chrome(kind, EditUiState(item = video, exporting = 0.3f, sheet = EditSheet.EXPORT))
            assertTrue(running.exportOverlay, "$kind")
            // The export's own screen stands in for its sheet.
            assertNull(running.sheet, "$kind")
            assertTrue(chrome(kind, EditUiState(item = video, savedToGallery = true)).exportOverlay, "$kind")
            // Shared, the screen moves on rather than staying under the export.
            val shared = chrome(kind, EditUiState(item = video, exported = true))
            assertFalse(shared.exportOverlay, "$kind")
            assertTrue(shared.leave, "$kind")
        }
        assertFalse(chrome(LayoutKind.Phone, EditUiState(exporting = 0.3f)).exportOverlay)
    }

    @Test
    fun aWideWindowStaysAfterASharedExport() {
        assertFalse(chrome(LayoutKind.Wide, EditUiState(item = video, exported = true)).leave)
    }

    @Test
    fun theSheetShowsWhileNothingCoversIt() {
        assertEquals(
            EditSheet.STYLE,
            chrome(LayoutKind.Phone, EditUiState(item = video, sheet = EditSheet.STYLE)).sheet,
        )
    }

    @Test
    fun escapePutsDownTheWideExportsResultElseTheGraph() {
        val done = chrome(LayoutKind.Wide, EditUiState(item = video, savedToGallery = true))
        assertEquals(EditEscape.ACKNOWLEDGE, done.escape(graphSelected = true))
        val running = chrome(LayoutKind.Wide, EditUiState(item = video, exporting = 0.5f))
        assertEquals(EditEscape.NONE, running.escape(graphSelected = true))
        assertEquals(EditEscape.DESELECT, chrome(LayoutKind.Phone).escape(graphSelected = true))
        assertEquals(EditEscape.NONE, chrome(LayoutKind.Phone).escape(graphSelected = false))
        // A tabletop's export screen is the phone's: Escape still puts the graph down under it.
        val tabletop = chrome(LayoutKind.Stacked, EditUiState(item = video, savedToGallery = true))
        assertEquals(EditEscape.DESELECT, tabletop.escape(graphSelected = true))
    }

    @Test
    fun soundUnlessMutedOrRecordedWithoutIt() {
        assertTrue(playsSound(muted = false, video))
        assertFalse(playsSound(muted = true, video))
        assertFalse(playsSound(muted = false, video.copy(hasAudio = false)))
        assertFalse(playsSound(muted = false, null))
        assertFalse(chrome(LayoutKind.Phone, muted = true).sound)
    }

    @Test
    fun theGraphPageIsTheGraphsSheetElseTheLastOne() {
        assertEquals(EditSheet.DATA, graphPage(EditSheet.DATA, EditSheet.STYLE))
        assertEquals(EditSheet.DATA, graphPage(EditSheet.EXPORT, EditSheet.DATA))
        assertEquals(EditSheet.STYLE, graphPage(null, EditSheet.STYLE))
    }

    @Test
    fun theExportSheetsTitleSaysHowTheExportIsGoing() {
        val ui = EditUiState(item = video)
        assertEquals(Res.string.edit_style.text(), sheetTitle(EditSheet.STYLE, ui))
        assertEquals(Res.string.edit_export.text(), sheetTitle(EditSheet.EXPORT, ui))
        assertEquals(Res.string.edit_making_video.text(), sheetTitle(EditSheet.EXPORT, ui.copy(exporting = 0.1f)))
        assertEquals(Res.string.edit_saved.text(), sheetTitle(EditSheet.EXPORT, ui.copy(savedToGallery = true)))
    }

    @Test
    fun theShotsDetailIsItsLengthPhasesAndHowItIsLinedUp() {
        assertNull(shotDetail(video))
        val shot = SampleShots.zer0
        val recorded = withShot.copy(shotStartInVideoUs = 0, syncLocked = true)
        val phases = shot.phaseStartsUs.size
        val expected =
            listOfNotNull(
                clock(shot.durationUs).raw(),
                Res.string.edit_phases_count.text("$phases").takeIf { phases > 0 },
                Res.string.edit_recorded_with_video.text(),
            )
        assertEquals(UiText.Joined(expected), shotDetail(recorded))
        val loose = shotDetail(withShot) as UiText.Joined
        assertEquals(shotStatus(withShot).text(), loose.parts.last())
    }

    @Test
    fun theTitleIsTheShotElseTheFile() {
        assertEquals("take.mp4".raw(), editTitle(video))
        assertEquals(SampleShots.zer0.profileName.raw(), editTitle(withShot))
    }

    @Test
    fun theShotChipsDotSaysWhetherTheShotIsPlaced() {
        assertEquals(ShotMark.NONE, shotMark(null))
        assertEquals(ShotMark.NONE, shotMark(video))
        assertEquals(ShotMark.UNPLACED, shotMark(withShot))
        assertEquals(ShotMark.PLACED, shotMark(withShot.copy(shotStartInVideoUs = 1_000_000)))
    }

    @Test
    fun opacityIsInWholePercent() {
        assertEquals(42, opacityPercent(0.42f))
        assertEquals(100, opacityPercent(1f))
        assertEquals(0, opacityPercent(0.004f))
    }

    @Test
    fun formatsRoundTripThroughThePickersIndex() {
        for (f in VideoFormat.entries) assertEquals(f, formatAt(formatIndex(f)))
    }

    @Test
    fun theZoomKeyGoesInFromFittedAndBackFromAnyZoom() {
        assertEquals(ZOOM_IN_FACTOR, nextZoom(null))
        assertNull(nextZoom(ZOOM_IN_FACTOR))
        assertNull(nextZoom(1.7f))
    }

    @Test
    fun onlyThePhoneHasKeysOnThePictureAndOnlyATabletopTheFormats() {
        val phone = pictureChrome(LayoutKind.Phone)
        assertTrue(phone.toolbar && phone.frameSwitch && !phone.formatStrip)
        assertEquals(HintPlace.TOP_START, phone.hint)
        val wide = pictureChrome(LayoutKind.Wide)
        assertTrue(!wide.toolbar && !wide.frameSwitch && !wide.formatStrip)
        assertEquals(HintPlace.BOTTOM_CENTER, wide.hint)
        val stacked = pictureChrome(LayoutKind.Stacked)
        assertTrue(!stacked.toolbar && !stacked.frameSwitch && stacked.formatStrip)
    }
}
