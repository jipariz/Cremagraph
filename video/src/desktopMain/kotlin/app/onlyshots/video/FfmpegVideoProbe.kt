package app.onlyshots.video

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import org.bytedeco.javacv.Java2DFrameConverter

/** Duration, thumbnails and the audio of a video file on the desktop, through FFmpeg. */
class FfmpegVideoProbe : VideoProbe {
    override suspend fun info(file: Path): VideoInfo =
        withContext(Dispatchers.IO) {
            openGrabber(file).use { g ->
                VideoInfo(
                    durationUs = g.lengthInTime,
                    hasAudio = g.audioChannels > 0,
                    heightPx = g.displaySize.second,
                    widthPx = g.displaySize.first,
                    recordedAt = parseRecordedAt(g.getMetadata("creation_time") ?: g.getVideoMetadata("creation_time")),
                )
            }
        }

    override suspend fun writeThumbnail(file: Path, atUs: Long, out: Path, maxWidthPx: Int) =
        withContext(Dispatchers.IO) {
            openGrabber(file, audio = false).use { g ->
                val frame =
                    g.pictureAt(atUs.coerceIn(0, (g.lengthInTime - 1).coerceAtLeast(0)))
                        ?: error("no frame at $atUs µs")
                val picture = frame.use { Java2DFrameConverter().convert(it) }
                val rotation = g.uprightRotation
                val (dw, dh) = g.displaySize
                val scale = (maxWidthPx.toFloat() / dw).coerceAtMost(1f)
                val w = (dw * scale).toInt().coerceAtLeast(1)
                val h = (dh * scale).toInt().coerceAtLeast(1)
                val thumb = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                val g2 = thumb.createGraphics()
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.translate(w / 2.0, h / 2.0)
                g2.rotate(Math.toRadians(rotation.toDouble()))
                g2.scale(scale.toDouble(), scale.toDouble())
                g2.drawImage(picture, -picture.width / 2, -picture.height / 2, null)
                g2.dispose()
                out.parent?.toFile()?.mkdirs()
                ImageIO.write(thumb, "jpg", out.toFile())
                Unit
            }
        }

    override suspend fun decodeMonoPcm(file: Path, maxSeconds: Int): MonoPcm? =
        withContext(Dispatchers.IO) {
            openGrabber(file).use { g ->
                if (g.audioChannels <= 0) return@use null
                val rate = g.sampleRate
                val channels = g.audioChannels
                val max = rate * maxSeconds
                val mono = FloatArray(max)
                var n = 0
                while (n < max) {
                    val frame = g.grabSamples() ?: break
                    val buffers = frame.samples ?: continue
                    // Planar (one buffer per channel) or packed (channels interleaved); floats or 16-bit.
                    val planar = buffers.size > 1
                    val count = if (planar) buffers[0].limit() else buffers[0].limit() / channels
                    for (i in 0 until count) {
                        if (n >= max) break
                        var sum = 0f
                        for (c in 0 until channels) sum +=
                            sample(buffers[if (planar) c else 0], if (planar) i else i * channels + c)
                        mono[n++] = sum / channels
                    }
                }
                MonoPcm(mono.copyOf(n), rate)
            }
        }

    private fun sample(b: java.nio.Buffer, i: Int): Float =
        when (b) {
            is FloatBuffer -> b.get(i)
            is ShortBuffer -> b.get(i) / 32768f
            else -> 0f
        }

    /** libx264 writes 2160 × 3840 on any desktop. */
    override fun supports4k(): Boolean = true
}
