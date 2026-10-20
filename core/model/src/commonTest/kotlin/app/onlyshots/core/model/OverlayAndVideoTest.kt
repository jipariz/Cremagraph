package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class OverlayAndVideoTest {
    @Test
    fun moveStaysInsideFrame() {
        val r = NormRect(0.1f, 0.1f, 0.5f, 0.2f).movedBy(2f, -2f)
        assertEquals(0.5f, r.x, 1e-6f)
        assertEquals(0f, r.y)
    }

    @Test
    fun resizeKeepsMinimumAndFrame() {
        val r = NormRect(0.6f, 0.85f, 0.35f, 0.12f)
        val small = r.resizedBy(-1f, -1f)
        assertEquals(NormRect.MIN_W, small.w, 1e-6f)
        assertEquals(NormRect.MIN_H, small.h, 1e-6f)
        val big = r.resizedBy(1f, 1f)
        assertEquals(1f, big.right, 1e-6f)
        assertEquals(1f, big.bottom, 1e-6f)
    }

    @Test
    fun resizeFromTheLeftKeepsTheRightEdge() {
        val r = NormRect(0.6f, 0.5f, 0.35f, 0.2f)
        val wider = r.resizedFromLeftBy(-0.2f, 0.1f)
        assertEquals(0.4f, wider.x, 1e-6f)
        assertEquals(r.right, wider.right, 1e-6f)
        assertEquals(0.3f, wider.h, 1e-6f)
        val narrow = r.resizedFromLeftBy(1f, 0f)
        assertEquals(NormRect.MIN_W, narrow.w, 1e-6f)
        assertEquals(r.right, narrow.right, 1e-6f)
        assertEquals(0f, r.resizedFromLeftBy(-1f, 0f).x, 1e-6f)
    }

    @Test
    fun snapBottomLeavesRoomForRecordBar() {
        val r = NormRect.Default.snappedTo(SnapPosition.BOTTOM)
        assertEquals(NormRect.REF_H - 70f, r.bottom * NormRect.REF_H, 0.01f)
        val top = NormRect.Default.snappedTo(SnapPosition.TOP)
        assertEquals(14f, top.y * NormRect.REF_H, 0.01f)
    }

    @Test
    fun opacitySnapsToFivePercentSteps() {
        assertEquals(0.35f, OverlaySettings.snapOpacity(0.36f), 1e-6f)
        // The background can fade out completely; below 0 and above 1 are not settings.
        assertEquals(0f, OverlaySettings.snapOpacity(0.01f))
        assertEquals(1f, OverlaySettings.snapOpacity(3f))
        assertEquals(0.1f, OverlaySettings(opacity = 0.1f).opacity)
        assertFailsWith<IllegalArgumentException> { OverlaySettings(opacity = -0.1f) }
    }

    private val video =
        VideoItem(
            id = "v",
            file = "v.mp4",
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = 40_000_000,
            origin = Origin.IMPORTED,
            hasAudio = true,
            shot = null,
            shotStartInVideoUs = null,
            syncLocked = false,
            trimInUs = 0,
            trimOutUs = 40_000_000,
            overlay = OverlaySettings(),
        )

    @Test
    fun trimKeepsThreeSeconds() {
        val t = video.trimmed(20_000_000, 21_000_000)
        assertEquals(VideoItem.MIN_TRIM_US, t.trimmedDurationUs)
        val clamped = video.trimmed(-5, 99_000_000)
        assertEquals(0, clamped.trimInUs)
        assertEquals(40_000_000, clamped.trimOutUs)
    }

    @Test
    fun lockedFlagCannotMove() {
        assertEquals(5_000_000, video.withShotStart(5_000_000).shotStartInVideoUs)
        val locked = video.copy(syncLocked = true, shotStartInVideoUs = 3_000_000)
        assertEquals(3_000_000, locked.withShotStart(5_000_000).shotStartInVideoUs)
    }

    @Test
    fun capabilitiesExplainMissingSeries() {
        assertTrue(Series.WEIGHT !in Capabilities.of(SourceKind.GAGGIMATE).series)
        assertTrue(Series.WEIGHT in Capabilities.of(SourceKind.GAGGIMATE, hasScale = true).series)
        assertTrue(Series.PRESSURE !in Capabilities.of(SourceKind.SCALE).series)
        assertEquals(Trigger.FIRST_DROPS, Capabilities.of(SourceKind.SCALE).autoStartTrigger)
    }

    @Test
    fun formatting() {
        assertEquals("7.7", 7.66f.oneDecimal())
        assertEquals("0.0", (-0.01f).oneDecimal())
        assertEquals("-1.5", (-1.5f).oneDecimal())
        assertEquals("1:05", formatClock(65_400_000))
    }
}
