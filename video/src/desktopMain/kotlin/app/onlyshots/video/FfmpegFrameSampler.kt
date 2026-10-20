package app.onlyshots.video

import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import org.bytedeco.javacv.Frame

/** Luma at points of the frame shown at a time, on the desktop: for adapt-to-video and the export tests. */
class FfmpegFrameSampler : FrameSampler {
    override suspend fun lumaAt(file: Path, atUs: Long, points: List<Pair<Float, Float>>): List<Float>? =
        withContext(Dispatchers.IO) {
            openGrabber(file, audio = false).use { g ->
                val frame = g.pictureAt(atUs) ?: return@use null
                frame.use { lumas(it, g.uprightRotation, points) }
            }
        }

    private fun lumas(frame: Frame, rotation: Int, points: List<Pair<Float, Float>>): List<Float> {
        val buf = frame.image[0] as ByteBuffer
        val w = frame.imageWidth
        val h = frame.imageHeight
        val row = frame.imageStride
        return points.map { (x, y) ->
            // From the upright picture back to the stored one.
            val (sx, sy) =
                when (rotation) {
                    90 -> y to 1f - x
                    180 -> 1f - x to 1f - y
                    270 -> 1f - y to x
                    else -> x to y
                }
            val ix = (sx * w).toInt().coerceIn(0, w - 1)
            val iy = (sy * h).toInt().coerceIn(0, h - 1)
            val o = iy * row + ix * 4
            val b = buf.get(o).toInt() and 0xFF
            val gr = buf.get(o + 1).toInt() and 0xFF
            val r = buf.get(o + 2).toInt() and 0xFF
            (0.2126f * r + 0.7152f * gr + 0.0722f * b) / 255f
        }
    }
}
