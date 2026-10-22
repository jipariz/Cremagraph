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
        context(VideoFormat.PORTRAIT_9_16) {
            val r = NormRect(0.6f, 0.85f, 0.35f, 0.12f)
            val small = r.resizedFrom(NormRect.Corner.BOTTOM_RIGHT, -1f, -1f)
            assertEquals(NormRect.MIN_W, small.w, 1e-6f)
            assertEquals(NormRect.MIN_H, small.h, 1e-6f)
            val big = r.resizedFrom(NormRect.Corner.BOTTOM_RIGHT, 1f, 1f)
            assertEquals(1f, big.right, 1e-6f)
            assertEquals(1f, big.bottom, 1e-6f)
        }
    }

    @Test
    fun everyCornerResizesAndTheOppositeOneStays() {
        context(VideoFormat.PORTRAIT_9_16) {
            val r = NormRect(0.3f, 0.4f, 0.4f, 0.2f)
            for (corner in NormRect.Corner.entries) {
                val sx = if (corner.left) -1f else 1f
                val sy = if (corner.top) -1f else 1f
                val grown = r.resizedFrom(corner, 0.1f * sx, 0.05f * sy)
                assertEquals(0.5f, grown.w, 1e-6f, "$corner w")
                assertEquals(0.25f, grown.h, 1e-6f, "$corner h")
                if (corner.left) assertEquals(r.right, grown.right, 1e-6f) else assertEquals(r.x, grown.x, 1e-6f)
                if (corner.top) assertEquals(r.bottom, grown.bottom, 1e-6f) else assertEquals(r.y, grown.y, 1e-6f)

                val shrunk = r.resizedFrom(corner, -sx, -sy)
                assertEquals(NormRect.MIN_W, shrunk.w, 1e-6f, "$corner min w")
                assertEquals(NormRect.MIN_H, shrunk.h, 1e-6f, "$corner min h")
                if (corner.left) assertEquals(r.right, shrunk.right, 1e-6f) else assertEquals(r.x, shrunk.x, 1e-6f)
                if (corner.top) assertEquals(r.bottom, shrunk.bottom, 1e-6f) else assertEquals(r.y, shrunk.y, 1e-6f)

                val out = r.resizedFrom(corner, sx, sy)
                if (corner.left) assertEquals(0f, out.x, 1e-6f) else assertEquals(1f, out.right, 1e-6f)
                if (corner.top) assertEquals(0f, out.y, 1e-6f) else assertEquals(1f, out.bottom, 1e-6f)
            }
        }
    }

    @Test
    fun resizeStaysInsideBounds() {
        context(VideoFormat.PORTRAIT_9_16) {
            val r = NormRect(0.3f, 0.4f, 0.4f, 0.2f)
            val bounds = NormRect(0.2f, 0.3f, 0.6f, 0.5f)
            val topLeft = r.resizedFrom(NormRect.Corner.TOP_LEFT, -1f, -1f, within = bounds)
            assertEquals(0.2f, topLeft.x, 1e-6f)
            assertEquals(0.3f, topLeft.y, 1e-6f)
            val bottomRight = r.resizedFrom(NormRect.Corner.BOTTOM_RIGHT, 1f, 1f, within = bounds)
            assertEquals(0.8f, bottomRight.right, 1e-6f)
            assertEquals(0.8f, bottomRight.bottom, 1e-6f)
        }
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
    fun theShotStartStaysInsideTheTrim() {
        val v = video.copy(shotStartInVideoUs = 10_000_000).trimmed(5_000_000, 30_000_000)
        assertEquals(5_000_000, v.withShotStart(1_000_000).shotStartInVideoUs)
        assertEquals(30_000_000, v.withShotStart(35_000_000).shotStartInVideoUs)
    }

    @Test
    fun aTrimHandlePastTheStartCarriesIt() {
        val v = video.copy(shotStartInVideoUs = 10_000_000)
        assertEquals(12_000_000, v.trimmed(12_000_000, 40_000_000).shotStartInVideoUs)
        assertEquals(8_000_000, v.trimmed(0, 8_000_000).shotStartInVideoUs)
        // Not reached, it stays.
        assertEquals(10_000_000, v.trimmed(4_000_000, 35_000_000).shotStartInVideoUs)
    }

    @Test
    fun aLockedStartStopsTheTrimUntilUnlocked() {
        val locked = video.copy(shotStartInVideoUs = 10_000_000, syncLocked = true)
        val inPast = locked.trimmed(12_000_000, 40_000_000)
        assertEquals(10_000_000, inPast.trimInUs)
        assertEquals(10_000_000, inPast.shotStartInVideoUs)
        val outPast = locked.trimmed(0, 8_000_000)
        assertEquals(10_000_000, outPast.trimOutUs)
        assertEquals(0, outPast.trimInUs)
        assertEquals(12_000_000, locked.unlocked().trimmed(12_000_000, 40_000_000).shotStartInVideoUs)
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
