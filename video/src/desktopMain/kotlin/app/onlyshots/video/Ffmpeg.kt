package app.onlyshots.video

import java.nio.ByteBuffer
import kotlin.math.roundToInt
import okio.Path
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/*
 * FFmpeg (JavaCV) helpers shared by the desktop editor's probe, sampler, exporter and player. Frames are decoded as
 * BGRA, Skia's native order on the desktop, so a decoded frame becomes a Skia image with one copy.
 */

/** A grabber for [file] decoding pictures as BGRA, started; [audio] false skips the sound entirely. */
internal fun openGrabber(file: Path, audio: Boolean = true, sampleFormat: Int? = null): FFmpegFrameGrabber {
    FfmpegLog.quiet()
    return FFmpegFrameGrabber(file.toFile()).apply {
        pixelFormat = avutil.AV_PIX_FMT_BGRA
        if (!audio) audioStream = -1
        sampleFormat?.let { this.sampleFormat = it }
        start()
    }
}

/**
 * Degrees to rotate the decoded picture clockwise to show it upright. Phones store portrait video as landscape frames
 * with a display matrix; FFmpeg reports its rotation counter-clockwise.
 */
internal val FFmpegFrameGrabber.uprightRotation: Int
    get() = ((-displayRotation).roundToInt() % 360 + 360) % 360

/** The picture's size as shown, after [uprightRotation]. */
internal val FFmpegFrameGrabber.displaySize: Pair<Int, Int>
    get() = if (uprightRotation % 180 == 0) imageWidth to imageHeight else imageHeight to imageWidth

/**
 * The picture shown at [atUs]: the last frame that starts at or before it. Seeks to the keyframe before and decodes
 * forward, because JavaCV's own frame-accurate seek lands early in files that also carry sound. The returned frame is a
 * copy, safe after the next grab.
 */
internal fun FFmpegFrameGrabber.pictureAt(atUs: Long): Frame? {
    setTimestamp(atUs.coerceAtLeast(0), false)
    var shown: Frame? = null
    while (true) {
        val f = grabImage() ?: break
        if (f.timestamp > atUs && shown != null) break
        shown?.close()
        shown = f.clone()
        if (f.timestamp > atUs) break
    }
    return shown
}

/** A decoded BGRA picture frame as a Skia image (copied, so the frame can be reused by the grabber). */
internal fun Frame.toSkiaImage(): Image {
    val buffer = image[0] as ByteBuffer
    val rowBytes = imageStride * (imageDepth.coerceAtLeast(8) / 8).coerceAtLeast(1)
    val bytes = ByteArray(rowBytes * imageHeight)
    buffer.duplicate().apply { rewind() }.get(bytes, 0, bytes.size.coerceAtMost(buffer.capacity()))
    return Image.makeRaster(
        ImageInfo(imageWidth, imageHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
        bytes,
        rowBytes,
    )
}

/** FFmpeg's console chatter off, once: decoding warnings are not the user's business. */
private object FfmpegLog {
    private var done = false

    fun quiet() {
        if (done) return
        done = true
        avutil.av_log_set_level(avutil.AV_LOG_ERROR)
        FFmpegLogCallback.setLevel(avutil.AV_LOG_ERROR)
    }
}
