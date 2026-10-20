package app.onlyshots.video

import app.onlyshots.overlay.AlignmentBarcode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath

class AlignmentCheckTest {
    /** A fake decoded video: picture shows frame n, the overlay was drawn for n + [lag]. */
    private class FakeVideo(val lag: Int) : FrameSampler {
        override suspend fun lumaAt(file: Path, atUs: Long, points: List<Pair<Float, Float>>): List<Float> {
            val n = AlignmentBarcode.frameIndex(atUs, 30)
            val overlayTop = AlignmentBarcode.overlayRowTop(AlignmentBarcode.SpikeRect)
            return points.map { (x, y) ->
                val frame = if (y >= overlayTop) n + lag else n
                val bit = (x * AlignmentBarcode.BITS).toInt()
                if (AlignmentBarcode.bit(frame, bit)) 0.95f else 0.05f
            }
        }
    }

    @Test
    fun alignedExportHasZeroOffset() = runTest {
        val r =
            checkAlignment(
                FakeVideo(lag = 0),
                "/out.mp4".toPath(),
                AlignmentBarcode.SpikeRect,
                listOf(5_000_000, 22_500_000, 40_000_000),
            )
        assertEquals(listOf(150, 675, 1200), r.map { it.pictureFrame })
        assertEquals(listOf(0, 0, 0), r.map { it.offsetFrames })
    }

    @Test
    fun reportsLagInFrames() = runTest {
        val r = checkAlignment(FakeVideo(lag = 60), "/out.mp4".toPath(), AlignmentBarcode.SpikeRect, listOf(10_000_000))
        assertEquals(60, r.single().offsetFrames)
    }
}
