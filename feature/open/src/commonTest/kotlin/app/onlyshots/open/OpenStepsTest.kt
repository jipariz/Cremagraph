package app.onlyshots.open

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.open_change
import app.onlyshots.core.resources.open_choose
import app.onlyshots.core.resources.open_drop_to_use
import app.onlyshots.core.resources.open_error_codec
import app.onlyshots.core.resources.open_error_not_a_shot
import app.onlyshots.core.resources.open_error_unreadable
import app.onlyshots.core.resources.open_hint_add_shot
import app.onlyshots.core.resources.open_hint_add_video
import app.onlyshots.core.resources.open_hint_both
import app.onlyshots.core.resources.open_hint_ready
import app.onlyshots.core.resources.open_mobile_shot_detail
import app.onlyshots.core.resources.open_mobile_video_detail
import app.onlyshots.core.resources.open_phone_shot_machine
import app.onlyshots.core.resources.open_phone_video_gallery
import app.onlyshots.core.resources.open_recorded
import app.onlyshots.core.resources.open_shot_file_detail
import app.onlyshots.core.resources.open_shot_from
import app.onlyshots.core.resources.open_shot_weight_in
import app.onlyshots.core.resources.open_video
import app.onlyshots.core.resources.open_wide_shot_detail
import app.onlyshots.core.resources.open_wide_shot_detail_machine
import app.onlyshots.core.resources.open_wide_video_drop
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.core.resources.unit_seconds
import app.onlyshots.video.UndecodableVideoException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OpenStepsTest {
    private val video =
        VideoItem(
            id = "v",
            file = "v.mp4",
            createdAt = Instant.parse("2026-10-19T08:11:42Z"),
            durationUs = 42_000_000,
            origin = Origin.IMPORTED,
            hasAudio = true,
            shot = null,
            shotStartInVideoUs = null,
            syncLocked = false,
            trimInUs = 2_000_000,
            trimOutUs = 32_000_000,
            overlay = OverlaySettings(),
            recordedAt = Instant.parse("2026-10-20T08:14:30Z"),
        )
    private val file = PickedFile("morning.mov", 1080, 1920)
    private val shot =
        Shot(
            id = "s",
            source = SourceKind.GAGGIUINO,
            profileName = "Zer0",
            startedAt = Instant.parse("2026-10-20T08:14:05Z"),
            durationUs = 28_600_000,
            finalWeight = 36.43f,
            samples = emptyList(),
            phaseStartsUs = emptyList(),
        )
    private val fromFile = ChosenShot(shot, "shot-1287.json", ShotOrigin.File)
    private val fromMachine = ChosenShot(shot, "Zer0", ShotOrigin.Machine)

    private val seconds = UiText.Joined(listOf("28".raw(), Res.string.unit_seconds.text()), " ")

    @Test
    fun theVideoStepAsksForAVideoInEachPagesWords() {
        val ui = OpenUi()
        assertEquals(Res.string.open_phone_video_gallery.text(), ui.videoDetail(StepWords.PhoneApp))
        assertEquals(Res.string.open_mobile_video_detail.text(), ui.videoDetail(StepWords.MobileWeb))
        assertEquals(Res.string.open_wide_video_drop.text(), ui.videoDetail(StepWords.Desk))
    }

    @Test
    fun aFileHeldOverTheWindowIsToBeDropped() {
        val ui = OpenUi(dragging = true)
        assertEquals(Res.string.open_drop_to_use.text(), ui.videoDetail(StepWords.PhoneApp))
        assertEquals(Res.string.open_drop_to_use.text(), ui.emptyVideoDetail(StepWords.Desk))
    }

    @Test
    fun openingTakesThePickedVideosPlace() {
        val ui = OpenUi(picked = video, status = Opening)
        for (words in StepWords.entries) {
            assertEquals(Opening, ui.videoDetail(words))
            assertEquals(Opening, ui.emptyVideoDetail(words))
        }
        assertNull(ui.problem)
    }

    @Test
    fun aProblemIsNotOpening() {
        val problem = Res.string.open_error_not_a_shot.text("x.shot")
        val ui = OpenUi(status = problem)
        assertEquals(problem, ui.problem)
        assertNull(ui.opening)
    }

    @Test
    fun aVideoThatWillNotOpenSaysWhy() {
        // The browser names the codec, so the message can say which one and what plays it.
        assertEquals(Res.string.open_error_codec.text("HEVC"), UndecodableVideoException("hevc").openProblem())
        // Nothing in the file was read as video: there is no codec to name.
        assertEquals(Res.string.open_error_unreadable.text(), UndecodableVideoException("").openProblem())
        // Anything else keeps the platform's own words.
        assertEquals("disk full".raw(), IllegalStateException("disk full").openProblem())
    }

    @Test
    fun thePhoneAppSaysWhenTheVideoWasFilmedAndHowLongItIsKept() {
        val ui = OpenUi(picked = video, pickedFile = file)
        assertEquals(UiText.Joined(listOf("2026-10-19 08:11".raw(), "0:30".raw())), ui.videoDetail(StepWords.PhoneApp))
    }

    @Test
    fun theWebSaysTheVideosLengthSizeAndWhenItWasRecorded() {
        val expected =
            UiText.Joined(listOf("0:42".raw(), "1080 × 1920".raw(), Res.string.open_recorded.text("2026-10-20 08:14")))
        val ui = OpenUi(picked = video, pickedFile = file)
        assertEquals(expected, ui.videoDetail(StepWords.MobileWeb))
        assertEquals(expected, ui.videoDetail(StepWords.Desk))
    }

    @Test
    fun anUnknownSizeAndNoRecordingTimeAreLeftOut() {
        val line = videoLine(video.copy(recordedAt = null), PickedFile("a.mp4", 0, 0))
        assertEquals(UiText.Joined(listOf("0:42".raw())), line)
    }

    @Test
    fun thePickedVideoIsCalledByItsFilesName() {
        assertEquals("morning.mov".raw(), videoName(file))
        assertEquals(Res.string.open_video.text(), videoName(null))
    }

    @Test
    fun theShotStepAsksForAFileOrTheMachineWhereOneIsReachable() {
        assertEquals(Res.string.open_phone_shot_machine.text(), OpenUi().shotDetail(StepWords.PhoneApp))
        assertEquals(
            Res.string.open_shot_file_detail.text(),
            OpenUi(fromMachine = false).shotDetail(StepWords.PhoneApp),
        )
        assertEquals(Res.string.open_mobile_shot_detail.text(), OpenUi().shotDetail(StepWords.MobileWeb))
        assertEquals(Res.string.open_wide_shot_detail_machine.text(), OpenUi().shotPrompt(StepWords.Desk))
        assertEquals(Res.string.open_wide_shot_detail.text(), OpenUi(fromMachine = false).shotPrompt(StepWords.Desk))
    }

    @Test
    fun thePhoneAppNamesAShotFromAFileByTheFileAndOneFromTheMachineByBoth() {
        assertEquals("shot-1287.json".raw(), OpenUi(shot = fromFile).shotDetail(StepWords.PhoneApp))
        assertEquals(
            Res.string.open_shot_from.text("Zer0", "Gaggiuino".raw()),
            OpenUi(shot = fromMachine).shotDetail(StepWords.PhoneApp),
        )
    }

    @Test
    fun theWebSaysTheShotsMachineWeightTimeAndWhen() {
        val grams = UiText.Joined(listOf("36.4".raw(), Res.string.unit_grams.text()), " ")
        val expected =
            UiText.Joined(
                listOf(
                    "Gaggiuino".raw(),
                    Res.string.open_shot_weight_in.text(grams, seconds),
                    "2026-10-20 08:14".raw(),
                )
            )
        assertEquals(expected, OpenUi(shot = fromFile).shotDetail(StepWords.Desk))
        assertEquals(expected, shotLine(fromMachine))
    }

    @Test
    fun aShotWithoutAWeightSaysOnlyItsTime() {
        val line = shotLine(fromFile.copy(shot = shot.copy(finalWeight = null)))
        assertEquals(UiText.Joined(listOf("Gaggiuino".raw(), seconds, "2026-10-20 08:14".raw())), line)
    }

    @Test
    fun aScaleIsAWordAndAMachineItsName() {
        assertEquals(Res.string.source_scale.text(), machineText(SourceKind.SCALE))
        assertEquals("Decent DE1".raw(), machineText(SourceKind.DECENT))
    }

    @Test
    fun theStepsGoInOrder() {
        val none = OpenUi()
        assertTrue(none.videoNext)
        assertFalse(none.shotNext)
        val video = OpenUi(picked = video)
        assertFalse(video.videoNext)
        assertTrue(video.shotNext)
        assertFalse(video.ready)
        val both = video.copy(shot = fromFile)
        assertFalse(both.shotNext)
        assertTrue(both.ready)
    }

    @Test
    fun theHintSaysWhatOpenEditorIsWaitingFor() {
        assertEquals(Res.string.open_hint_both.text(), OpenUi().ctaHint())
        assertEquals(Res.string.open_hint_add_shot.text(), OpenUi(picked = video).ctaHint())
        assertEquals(Res.string.open_hint_add_video.text(), OpenUi(shot = fromFile).ctaHint())
        assertEquals(Res.string.open_hint_ready.text(), OpenUi(picked = video, shot = fromFile).ctaHint())
    }

    @Test
    fun aStepsKeyChoosesThenChanges() {
        assertEquals(Res.string.open_choose.text(), pickAction(picked = false))
        assertEquals(Res.string.open_change.text(), pickAction(picked = true))
    }

    @Test
    fun stepNumbersHaveTwoDigits() {
        assertEquals("01", stepNumber(1))
        assertEquals("12", stepNumber(12))
    }
}
