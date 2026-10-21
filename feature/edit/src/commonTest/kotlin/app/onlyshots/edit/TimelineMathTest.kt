package app.onlyshots.edit

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_cut_both
import app.onlyshots.core.resources.edit_cut_head
import app.onlyshots.core.resources.edit_cut_tail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TimelineMathTest {
    private val width = 300f
    private val item =
        VideoItem(
            id = "v",
            file = "v.mp4",
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = 30_000_000,
            origin = Origin.IMPORTED,
            hasAudio = false,
            shot = null,
            shotStartInVideoUs = 15_000_000,
            syncLocked = false,
            trimInUs = 0,
            trimOutUs = 30_000_000,
            overlay = OverlaySettings(),
        )

    private val fitted = TimelineView(widthPx = width + 32f, durationUs = 30_000_000, playheadUs = 0, padPx = 16f)

    @Test
    fun fittedPixelsAndMicrosecondsRoundTripAtTheEdges() {
        for (us in listOf(0L, 1L, 15_000_000L, 29_999_999L, 30_000_000L)) {
            val back = fitted.us(fitted.x(us))
            assertEquals(us.toDouble(), back.toDouble(), 30_000_000.0 / width)
        }
        assertEquals(16f, fitted.x(0))
        assertEquals(width + 16f, fitted.x(30_000_000), 1e-3f)
    }

    @Test
    fun zoomedThePlayheadStaysInTheMiddle() {
        val zoomed = fitted.copy(playheadUs = 10_000_000, pxPerSecond = 60f)
        assertEquals(zoomed.widthPx / 2f, zoomed.x(10_000_000))
        assertEquals(zoomed.widthPx / 2f + 60f, zoomed.x(11_000_000), 1e-3f)
        assertEquals(1_000_000L, zoomed.usFor(60f))
    }

    @Test
    fun aPressNearAHandleGrabsItAndAnywhereElseScrubs() {
        assertEquals(TimelineHandle.TRIM_IN, TimelineMath.hitHandle(20f, item, fitted, grabPx = 22f))
        assertEquals(TimelineHandle.TRIM_OUT, TimelineMath.hitHandle(width + 10f, item, fitted, grabPx = 22f))
        assertEquals(TimelineHandle.PLAYHEAD, TimelineMath.hitHandle(80f, item, fitted, grabPx = 22f))
    }

    @Test
    fun aFlagOnATrimHandleIsGrabbedByItsTab() {
        val atStart = item.copy(shot = app.onlyshots.core.model.sample.SampleShots.zer0, shotStartInVideoUs = 0)
        val x = fitted.x(0)
        assertEquals(TimelineHandle.TRIM_IN, TimelineMath.hitHandle(x, atStart, fitted, grabPx = 22f))
        assertEquals(TimelineHandle.SHOT_FLAG, TimelineMath.hitHandle(x, atStart, fitted, 22f, onFlagTab = true))
    }

    @Test
    fun aTrimHandlePastThePlayheadPushesItToThatEdge() {
        val trimmedIn = item.trimmed(12_000_000, 30_000_000)
        assertEquals(12_000_000L, TimelineMath.pushedPlayhead(trimmedIn, 10_000_000))
        val trimmedOut = item.trimmed(0, 8_000_000)
        assertEquals(8_000_000L, TimelineMath.pushedPlayhead(trimmedOut, 10_000_000))
        assertEquals(null, TimelineMath.pushedPlayhead(trimmedIn, 20_000_000))
    }

    @Test
    fun theShotFlagIsOnlyGrabbableWithAShotThatIsNotLocked() {
        val x = fitted.x(15_000_000)
        assertEquals(TimelineHandle.PLAYHEAD, TimelineMath.hitHandle(x, item, fitted, grabPx = 22f))
        val withShot = item.copy(shot = app.onlyshots.core.model.sample.SampleShots.zer0)
        assertEquals(TimelineHandle.SHOT_FLAG, TimelineMath.hitHandle(x, withShot, fitted, grabPx = 22f))
        assertEquals(TimelineHandle.PLAYHEAD, TimelineMath.hitHandle(x, withShot.copy(syncLocked = true), fitted, 22f))
    }

    @Test
    fun snappingTakesTheNearestTargetWithinThePull() {
        val targets = listOf("playhead" to 10_000_000L, "pump start" to 10_300_000L, "shot start" to null)
        assertEquals(10_300_000L to "pump start", TimelineMath.snap(10_250_000, targets, pullUs = 200_000))
        assertEquals(12_000_000L to null, TimelineMath.snap(12_000_000, targets, pullUs = 200_000))
    }

    @Test
    fun theRulerStepLeavesRoomBetweenTicks() {
        assertEquals(5f, TimelineMath.rulerStep(pxPerSecond = 15f, minPx = 60f))
        assertEquals(0.5f, TimelineMath.rulerStep(pxPerSecond = 120f, minPx = 60f))
    }

    @Test
    fun aFrameStepStaysInsideTheTrim() {
        val trimmed = item.copy(trimInUs = 1_000_000, trimOutUs = 5_000_000)
        assertEquals(1_000_000L, TimelineMath.stepFrame(trimmed, 1_000_000, frames = -1))
        assertEquals(1_033_333L, TimelineMath.stepFrame(trimmed, 1_000_000, frames = 1))
    }

    @Test
    fun draggingTrimInPastTrimOutStopsAtTheMinimum() {
        val dragged = TimelineMath.drag(item, TimelineHandle.TRIM_IN, atUs = 29_000_000)
        assertEquals(VideoItem.MIN_TRIM_US, dragged.trimmedDurationUs)
    }

    @Test
    fun aRecordedShotStartIsLockedUntilUnlocked() {
        val x = fitted.x(15_000_000)
        val locked = item.copy(shot = app.onlyshots.core.model.sample.SampleShots.zer0, syncLocked = true)
        assertEquals(TimelineHandle.PLAYHEAD, TimelineMath.hitHandle(x, locked, fitted, grabPx = 22f))
        assertEquals(15_000_000L, locked.withShotStart(1_000_000).shotStartInVideoUs)
        val unlocked = locked.unlocked()
        assertEquals(15_000_000L, unlocked.shotStartInVideoUs, "unlocking leaves the flag where it was recorded")
        assertEquals(TimelineHandle.SHOT_FLAG, TimelineMath.hitHandle(x, unlocked, fitted, grabPx = 22f))
        assertEquals(1_000_000L, unlocked.withShotStart(1_000_000).shotStartInVideoUs)
    }

    @Test
    fun theHeaderSaysWhenTheTrimCutsIntoTheShot() {
        val shot = app.onlyshots.core.model.sample.SampleShots.zer0
        // A shot from 2 s, ending inside the clip.
        val withShot = item.copy(shot = shot, shotStartInVideoUs = 2_000_000, durationUs = shot.durationUs + 10_000_000)
        val end = withShot.shotEndInVideoUs!!
        assertEquals(2_000_000L + shot.durationUs, end)
        assertEquals(null, TimelineMath.shotCut(withShot.copy(trimInUs = 0, trimOutUs = end + 1_000_000)))
        assertEquals(null, TimelineMath.shotCut(withShot.copy(trimInUs = 2_000_000, trimOutUs = end)))
        assertEquals(
            Res.string.edit_cut_head,
            TimelineMath.shotCut(withShot.copy(trimInUs = 5_000_000, trimOutUs = end)),
        )
        assertEquals(
            Res.string.edit_cut_tail,
            TimelineMath.shotCut(withShot.copy(trimInUs = 0, trimOutUs = end - 1_000_000)),
        )
        assertEquals(
            Res.string.edit_cut_both,
            TimelineMath.shotCut(withShot.copy(trimInUs = 5_000_000, trimOutUs = end - 1_000_000)),
        )
        assertEquals(null, TimelineMath.shotCut(item.copy(trimInUs = 5_000_000)), "no shot, nothing to cut")
    }

    @Test
    fun aVideoThatEndsWithTheShotIsNotCut() {
        // A scale's take stops as the weight settles: the data runs 200 ms past the camera's file. Nothing was trimmed.
        val shot = app.onlyshots.core.model.sample.SampleShots.zer0
        val video = shot.durationUs + 2_800_000
        val take =
            item.copy(
                shot = shot,
                shotStartInVideoUs = 3_000_000,
                durationUs = video,
                trimInUs = 0,
                trimOutUs = video,
            )
        assertEquals(null, TimelineMath.shotCut(take))
        assertEquals(Res.string.edit_cut_tail, TimelineMath.shotCut(take.copy(trimOutUs = video - 2_000_000)))
        // A hair inside the shot is the clocks, not a cut.
        assertEquals(null, TimelineMath.shotCut(take.copy(trimInUs = 3_100_000)))
    }
}
