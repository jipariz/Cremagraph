package app.onlyshots.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlignmentBarcodeTest {
    @Test
    fun roundTripsEveryFrameOfFortyFiveSeconds() {
        for (n in 0..(45 * 30)) {
            val lumas = (0 until AlignmentBarcode.BITS).map { if (AlignmentBarcode.bit(n, it)) 0.9f else 0.1f }
            assertEquals(n, AlignmentBarcode.decode(lumas))
        }
    }

    @Test
    fun frameIndexRoundsToNearestFrame() {
        assertEquals(0, AlignmentBarcode.frameIndex(0, 30))
        assertEquals(1, AlignmentBarcode.frameIndex(33_333, 30))
        assertEquals(675, AlignmentBarcode.frameIndex(22_500_000, 30))
        assertEquals(1, AlignmentBarcode.frameIndex(20_000, 30)) // 0.6 of a frame rounds up
    }

    @Test
    fun bitsCoverFiveMinutesAtThirtyFps() {
        assertTrue((1 shl AlignmentBarcode.BITS) > 5 * 60 * 30)
    }

    @Test
    fun overlayRowIsInsideTheSpikeRect() {
        val r = AlignmentBarcode.SpikeRect
        val top = AlignmentBarcode.overlayRowTop(r)
        assertTrue(top >= r.y && top + AlignmentBarcode.ROW_HEIGHT <= r.bottom)
        assertTrue(top > AlignmentBarcode.ROW_HEIGHT, "rows do not overlap")
    }
}
