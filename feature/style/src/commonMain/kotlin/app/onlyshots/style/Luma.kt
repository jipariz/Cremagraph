package app.onlyshots.style

import androidx.compose.ui.graphics.ImageBitmap
import app.onlyshots.overlay.relativeLuminance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/*
 * How bright the picture is behind the preview's graph, for adapt to video and the readability guard: a stored frame's
 * once as it loads, the live camera's sampled over and over.
 */

/** A frame of the user's video to preview on, with how bright it is where the graph sits. */
class PreviewFrame(val image: ImageBitmap, val luma: Float)

/** Mean luminance of [image]'s middle third, where the graph sits in the preview. */
fun averageLuma(image: ImageBitmap): Float {
    val w = image.width
    val h = image.height
    if (w == 0 || h == 0) return SCENE_LUMA
    val rows = (h / 3).coerceAtLeast(1)
    val buf = IntArray(w * rows)
    image.readPixels(buf, 0, h / 3, w, rows)
    return averageLuma(buf)
}

/** Mean luminance of every [LUMA_STRIDE]th of [pixels] (ARGB); [SCENE_LUMA] when there are none. */
fun averageLuma(pixels: IntArray): Float {
    var sum = 0f
    var n = 0
    for (i in pixels.indices step LUMA_STRIDE) {
        sum += relativeLuminance(pixels[i])
        n++
    }
    return if (n == 0) SCENE_LUMA else sum / n
}

/** [now] folded into [previous] half way, as the record screen smooths it; the first sample as it is. */
fun smoothLuma(previous: Float?, now: Float): Float = previous?.let { it + (now - it) * LUMA_SMOOTHING } ?: now

/**
 * The live camera's brightness behind the graph: [sample] twice a second, smoothed. A sample that comes back empty (no
 * frame yet) keeps the last value.
 */
fun cameraLuma(sample: () -> Float?): Flow<Float> = flow {
    var luma: Float? = null
    while (true) {
        val now = sample()
        if (now != null) {
            val smoothed = smoothLuma(luma, now)
            luma = smoothed
            emit(smoothed)
        }
        delay(CAMERA_SAMPLE_MS)
    }
}

/** The drawn scene behind the graph is dark. */
const val SCENE_LUMA = 0.06f

/** Every seventh pixel is plenty for an average, and keeps a 720 px frame cheap. */
private const val LUMA_STRIDE = 7
private const val LUMA_SMOOTHING = 0.5f
private const val CAMERA_SAMPLE_MS = 500L
