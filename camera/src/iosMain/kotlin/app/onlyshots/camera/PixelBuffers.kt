@file:OptIn(ExperimentalForeignApi::class)

package app.onlyshots.camera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.math.pow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly

/**
 * BGRA pixel buffers (camera frames, player frames) as small Compose bitmaps: on iOS the native preview layers cannot
 * be blurred by Haze, so while a style blurs the video the picture is drawn from these instead.
 */
object PixelBuffers {
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
    private val owner = Data.makeUninitialized(1)

    /** [buffer] scaled to at most [maxWidth] px wide; null when it is empty. */
    fun toImageBitmap(buffer: CVPixelBufferRef, maxWidth: Int): ImageBitmap? {
        CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        try {
            val w = CVPixelBufferGetWidth(buffer).toInt()
            val h = CVPixelBufferGetHeight(buffer).toInt()
            if (w == 0 || h == 0) return null
            val row = CVPixelBufferGetBytesPerRow(buffer).toInt()
            val base = CVPixelBufferGetBaseAddress(buffer) ?: return null
            val data = Data.makeWithoutCopy(base.rawValue, row * h, owner)
            val src = Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL), data, row)
            val scale = minOf(1f, maxWidth / w.toFloat())
            val dw = (w * scale).toInt().coerceAtLeast(1)
            val dh = (h * scale).toInt().coerceAtLeast(1)
            val surface = Surface.makeRasterN32Premul(dw, dh)
            surface.canvas.drawImageRect(
                src,
                Rect.makeWH(w.toFloat(), h.toFloat()),
                Rect.makeWH(dw.toFloat(), dh.toFloat()),
                sampling,
                null,
                true,
            )
            val out = surface.makeImageSnapshot()
            src.close()
            data.close()
            surface.close()
            return out.toComposeImageBitmap()
        } finally {
            CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        }
    }

    /**
     * Relative luminance (linear light) on a [cols] × [rows] grid over [buffer], row by row: enough to tell how bright
     * any part of the picture is without keeping the frame.
     */
    fun lumaGrid(buffer: CVPixelBufferRef, cols: Int, rows: Int): FloatArray? {
        CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        try {
            val w = CVPixelBufferGetWidth(buffer).toInt()
            val h = CVPixelBufferGetHeight(buffer).toInt()
            val row = CVPixelBufferGetBytesPerRow(buffer).toInt()
            val base = CVPixelBufferGetBaseAddress(buffer)?.rawValue ?: return null
            if (w == 0 || h == 0) return null
            val bytes = kotlinx.cinterop.interpretCPointer<kotlinx.cinterop.ByteVar>(base) ?: return null
            fun lin(v: Int) = (v / 255f).let { if (it <= 0.03928f) it / 12.92f else ((it + 0.055f) / 1.055f).pow(2.4f) }
            return FloatArray(cols * rows) { i ->
                val x = ((i % cols + 0.5f) / cols * w).toInt().coerceIn(0, w - 1)
                val y = ((i / cols + 0.5f) / rows * h).toInt().coerceIn(0, h - 1)
                val o = y * row + x * 4
                val b = bytes[o].toInt() and 0xFF
                val g = bytes[o + 1].toInt() and 0xFF
                val r = bytes[o + 2].toInt() and 0xFF
                0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
            }
        } finally {
            CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        }
    }
}

/** The mean of the cells of a [cols] × [rows] luminance grid that fall in the normalized rect. */
internal fun FloatArray.meanIn(cols: Int, rows: Int, x: Float, y: Float, w: Float, h: Float): Float? {
    var sum = 0f
    var n = 0
    for (r in 0 until rows) for (c in 0 until cols) {
        val cx = (c + 0.5f) / cols
        val cy = (r + 0.5f) / rows
        if (cx in x..(x + w) && cy in y..(y + h)) {
            sum += this[r * cols + c]
            n++
        }
    }
    return if (n == 0) null else sum / n
}
