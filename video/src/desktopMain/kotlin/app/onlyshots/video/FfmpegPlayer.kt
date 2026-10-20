package app.onlyshots.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.max
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.Path
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Plays a video file for the desktop editor: frames as upright Compose bitmaps (at most [maxWidth] wide, enough for a
 * preview), the sound through the computer's speakers. One decoding thread owns the FFmpeg grabber; every call hops
 * onto it. [close] when the surface goes away.
 */
class FfmpegPlayer(private val file: Path, private val maxWidth: Int = 720) : AutoCloseable {
    private val thread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "onlyshots-player").apply { isDaemon = true }
    }
    private val decoder = thread.asCoroutineDispatcher()
    private var grabber: FFmpegFrameGrabber? = null
    private var surface: Surface? = null
    private var line: SourceDataLine? = null
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

    private fun grabber(): FFmpegFrameGrabber =
        grabber ?: openGrabber(file, sampleFormat = avutil.AV_SAMPLE_FMT_S16).also { grabber = it }

    /** The picture shown at [atUs], for a paused or scrubbed preview; null when nothing decodes there. */
    suspend fun frameAt(atUs: Long): ImageBitmap? =
        withContext(decoder) { grabber().pictureAt(atUs)?.use { upright(it) } }

    /**
     * Plays from [fromUs] in real time until cancelled, looping inside [clipUs]: each picture goes to [onFrame] with
     * its time, the sound to the speakers unless [muted] says otherwise at that moment.
     */
    suspend fun play(
        fromUs: Long,
        clipUs: LongRange,
        muted: () -> Boolean,
        onFrame: suspend (ImageBitmap, Long) -> Unit,
    ) =
        withContext(decoder) {
            val g = grabber()
            var start = fromUs.coerceIn(clipUs.first, max(clipUs.first, clipUs.last - 1))
            // Paused or seeking: the sound already queued stops with the picture.
            currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { line?.flush() }
            while (true) {
                g.setTimestamp(start, false)
                val wallStart = System.nanoTime()
                fun dueNs(us: Long) = wallStart + (us - start) * 1_000
                line?.flush()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val f = g.grabFrame(true, true, true, false) ?: break
                    val t = f.timestamp
                    if (t < start) continue
                    if (t >= clipUs.last) break
                    if (f.image != null) {
                        val wait = (dueNs(t) - System.nanoTime()) / 1_000_000
                        // Running late (a slow decode): drop the picture rather than fall further behind.
                        if (wait < -100) continue
                        if (wait > 0) delay(wait)
                        onFrame(upright(f), t)
                    } else if (f.samples != null && !muted()) {
                        speak(f)
                    }
                }
                start = clipUs.first
            }
        }

    private fun speak(f: Frame) {
        val samples = f.samples?.firstOrNull() as? ShortBuffer ?: return
        val out =
            line
                ?: AudioSystem.getSourceDataLine(AudioFormat(f.sampleRate.toFloat(), 16, f.audioChannels, true, false))
                    .apply {
                        open(format, f.sampleRate * f.audioChannels) // about half a second of sound
                        start()
                    }
                    .also { line = it }
        val bytes = ByteBuffer.allocate(samples.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(samples.duplicate())
        out.write(bytes.array(), 0, bytes.capacity())
    }

    /** A decoded frame turned upright and scaled down to the preview's size. */
    private fun upright(f: Frame): ImageBitmap {
        val g = grabber()
        val rotation = g.uprightRotation
        val (dw, dh) = g.displaySize
        val scale = (maxWidth.toFloat() / dw).coerceAtMost(1f)
        val w = (dw * scale).toInt().coerceAtLeast(1)
        val h = (dh * scale).toInt().coerceAtLeast(1)
        val s =
            surface?.takeIf { it.width == w && it.height == h }
                ?: Surface.makeRasterN32Premul(w, h).also {
                    surface?.close()
                    surface = it
                }
        val image = f.toSkiaImage()
        val canvas = s.canvas
        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.rotate(rotation.toFloat())
        canvas.scale(scale, scale)
        canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeXYWH(-image.width / 2f, -image.height / 2f, image.width.toFloat(), image.height.toFloat()),
            sampling,
            null,
            true,
        )
        canvas.restore()
        image.close()
        return s.makeImageSnapshot().toComposeImageBitmap()
    }

    override fun close() {
        thread.execute {
            line?.run {
                stop()
                close()
            }
            line = null
            grabber?.run {
                stop()
                release()
            }
            grabber = null
            surface?.close()
            surface = null
        }
        thread.shutdown()
    }
}
