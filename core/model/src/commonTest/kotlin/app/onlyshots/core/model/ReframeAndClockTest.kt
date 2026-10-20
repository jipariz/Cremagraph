package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReframeAndClockTest {
    private val shot = shotOf(s(0.0, pressure = 0f), s(30.0, pressure = 9f)) // starts 2026-10-11T08:14:00Z

    private fun video(recordedAt: Instant?, durationUs: Long = 60_000_000) =
        VideoItem(
            id = "v",
            file = "v.mp4",
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = durationUs,
            origin = Origin.IMPORTED,
            hasAudio = true,
            shot = shot,
            shotStartInVideoUs = null,
            syncLocked = false,
            trimInUs = 0,
            trimOutUs = durationUs,
            overlay = OverlaySettings(),
            recordedAt = recordedAt,
        )

    @Test
    fun theIdentityMapsEveryPointToItself() {
        val (x, y) = Reframe().map(0.2f, 0.7f)
        assertEquals(0.2f, x, 1e-6f)
        assertEquals(0.7f, y, 1e-6f)
    }

    @Test
    fun theCentreOfAReframeLandsInTheMiddleAndDistancesGrowByTheZoom() {
        val r = Reframe(zoom = 2f, x = 0.3f, y = 0.6f)
        assertEquals(0.5f to 0.5f, r.map(0.3f, 0.6f))
        val (fx, fy) = r.map(0.4f, 0.7f)
        assertEquals(0.7f, fx, 1e-5f)
        assertEquals(0.7f, fy, 1e-5f)
    }

    @Test
    fun clampingKeepsTheZoomInRangeAndThePictureCoveringTheFrame() {
        assertEquals(Reframe(), Reframe(zoom = 0.5f, x = 0.1f, y = 0.9f).clamped())
        val r = Reframe(zoom = 2f, x = 0.05f, y = 0.99f).clamped()
        assertEquals(Reframe(2f, 0.25f, 0.75f), r)
        // The picture's edges reach the frame's: nothing outside the picture is shown.
        assertEquals(0f, r.map(0f, 0f).first, 1e-5f)
        assertEquals(1f, r.map(1f, 1f).second, 1e-5f)
        assertEquals(Reframe.MAX_ZOOM, Reframe(zoom = 10f).clamped().zoom)
    }

    @Test
    fun aShotTheCameraFilmedStartsWhereTheClocksPutIt() {
        val v = video(recordedAt = shot.startedAt - 4.seconds)
        assertEquals(4_000_000, v.shotStartByClock())
    }

    @Test
    fun clocksThatPutTheShotOutsideTheClipDoNotCount() {
        assertNull(video(recordedAt = shot.startedAt + 1.seconds).shotStartByClock())
        assertNull(video(recordedAt = shot.startedAt - 2_000.seconds).shotStartByClock())
    }

    @Test
    fun noClockNoMatch() {
        assertNull(video(recordedAt = null).shotStartByClock())
        assertNull(video(recordedAt = shot.startedAt).copy(shot = null).shotStartByClock())
    }

    @Test
    fun theTitleIsTheUsersOwnOrProfileAndMachine() {
        assertEquals(
            "${shot.profileName} · ${machineName(shot.source, "scale")}",
            overlayTitle(shot, OverlaySettings(), "scale"),
        )
        assertEquals("Sunday", overlayTitle(shot, OverlaySettings(title = "Sunday"), "scale"))
        assertEquals(
            overlayTitle(shot, OverlaySettings(), "scale"),
            overlayTitle(shot, OverlaySettings(title = "  "), "scale"),
        )
    }
}
