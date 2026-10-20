package app.onlyshots.video

import androidx.compose.ui.graphics.toPixelMap
import app.onlyshots.overlay.AlignmentBarcode
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toOkioPath

class FfmpegPlayerTest {
    private val clip = File(System.getProperty("onlyshots.spikeClip") ?: "testdata/${SpikeClip.NAME}").toOkioPath()

    /** The frame number the spike clip's barcode shows in a preview bitmap. */
    private fun frameNumber(bitmap: androidx.compose.ui.graphics.ImageBitmap): Int {
        val px = bitmap.toPixelMap()
        val lumas =
            (0 until AlignmentBarcode.BITS).map {
                val (x, y) = AlignmentBarcode.blockCentre(it, AlignmentBarcode.pictureRowTop())
                val c =
                    px[
                        (x * px.width).toInt().coerceIn(0, px.width - 1),
                        (y * px.height).toInt().coerceIn(0, px.height - 1)]
                0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            }
        return AlignmentBarcode.decode(lumas)
    }

    @Test
    fun aPausedPreviewShowsTheFrameAtThePlayhead() = runBlocking {
        FfmpegPlayer(clip, maxWidth = 360).use { player ->
            val frame = assertNotNull(player.frameAt(5_000_000))
            assertEquals(360, frame.width)
            assertEquals(640, frame.height, "upright and 9:16")
            assertEquals(150, frameNumber(frame))
        }
    }

    @Test
    fun playingRunsInRealTimeAndLoopsInsideTheClip() = runBlocking {
        FfmpegPlayer(clip, maxWidth = 360).use { player ->
            val times = ArrayList<Long>()
            val started = System.nanoTime()
            withTimeoutOrNull(1_500) {
                player.play(10_000_000, 10_000_000L..11_000_000L, muted = { true }) { _, t -> times += t }
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(times.size in 30..50, "${times.size} frames in $elapsedMs ms")
            assertTrue(times.all { it in 10_000_000L..11_000_000L }, "inside the clip")
            assertTrue(times.zipWithNext().any { (a, b) -> b < a }, "looped back to the clip's start")
            assertTrue(abs(times.first() - 10_000_000L) < 40_000, "starts at the playhead: ${times.first()}")
        }
    }
}
