package app.onlyshots.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import app.onlyshots.core.model.Reframe
import app.onlyshots.overlay.glass.GlassShader
import app.onlyshots.overlay.scene.BackdropPrim
import kotlin.math.max
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

/**
 * Draws one decoded frame into a [width] × [height] output with Skia: the picture rotated by [rotation], crop-filled
 * and zoomed and moved by [reframe] as the editor's preview shows it, the video behind blur and glass panels blurred or
 * turned to glass, and the overlay on top. Shared by the exporters that draw on the CPU (iOS, the desktop editor); each
 * wraps its own decoded and encoded pixels as an [Image] and a [Surface].
 */
internal class SkiaFrameDrawer(
    private val width: Int,
    private val height: Int,
    private val rotation: Int,
    private val reframe: Reframe = Reframe(),
    /** Where the blur's small copy is drawn: the CPU, or the GPU context the frame is drawn on (the web). */
    makeSurface: (width: Int, height: Int) -> Surface = { w, h -> Surface.makeRasterN32Premul(w, h) },
) {
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

    /** A quarter-size copy of the frame to blur: a big blur on the CPU is only affordable small. */
    private val small =
        makeSurface((width / BLUR_DOWNSCALE).coerceAtLeast(1), (height / BLUR_DOWNSCALE).coerceAtLeast(1))

    /** Haze's glass shader (see GlassShader), compiled once per export. */
    private val glass: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GlassShader.sksl) }

    /**
     * [frame] drawn into [surface], then [backdrops] and the [overlay] bitmap at its left and top. [frame] is the
     * picture as decoded, before [rotation].
     */
    fun draw(
        frame: Image,
        surface: Surface,
        overlay: Triple<ImageBitmap, Int, Int>?,
        backdrops: List<BackdropPrim> = emptyList(),
    ) {
        val canvas = surface.canvas
        val sw = frame.width
        val sh = frame.height
        // Crop-fill: scale the displayed (rotated) picture to cover the output, centred.
        val dispW = if (rotation % 180 == 0) sw else sh
        val dispH = if (rotation % 180 == 0) sh else sw
        val scale = max(width / dispW.toFloat(), height / dispH.toFloat())
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        // The reframe's centre moved to the frame's, then zoomed about it: Reframe.map in canvas steps.
        val z = reframe.zoom
        canvas.translate((0.5f - reframe.x) * z * width, (0.5f - reframe.y) * z * height)
        canvas.scale(z, z)
        canvas.rotate(rotation.toFloat())
        canvas.scale(scale, scale)
        canvas.drawImageRect(
            frame,
            Rect.makeWH(sw.toFloat(), sh.toFloat()),
            Rect.makeXYWH(-sw / 2f, -sh / 2f, sw.toFloat(), sh.toFloat()),
            sampling,
            null,
            true,
        )
        canvas.restore()
        if (backdrops.isNotEmpty()) blurBehind(surface, backdrops)
        if (overlay != null) {
            val (bitmap, left, top) = overlay
            val overlayImage = Image.makeFromBitmap(bitmap.asSkiaBitmap())
            canvas.drawImage(overlayImage, left.toFloat(), top.toFloat())
            overlayImage.close()
        }
    }

    /**
     * The video behind a blur or glass style, blurred inside each backdrop's rounded rect before the graph is drawn
     * over it — what Haze shows in the preview and the Android exporter does with a shader.
     */
    private fun blurBehind(surface: Surface, backdrops: List<BackdropPrim>) {
        val frame = surface.makeImageSnapshot()
        val sigma = backdrops.maxOf { it.blur } * SIGMA_PER_RADIUS / BLUR_DOWNSCALE
        val paint = Paint()
        paint.imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
        small.canvas.drawImageRect(
            frame,
            Rect.makeWH(width.toFloat(), height.toFloat()),
            Rect.makeWH(small.width.toFloat(), small.height.toFloat()),
            sampling,
            paint,
            true,
        )
        paint.close()
        val blurred = small.makeImageSnapshot()
        val canvas = surface.canvas
        for (b in backdrops) {
            if (b.glass != null) {
                drawGlass(canvas, frame, blurred, b)
                continue
            }
            canvas.save()
            canvas.clipRRect(
                RRect.makeLTRB(b.box.left, b.box.top, b.box.right, b.box.bottom, b.radius),
                ClipMode.INTERSECT,
                true,
            )
            canvas.drawImageRect(
                blurred,
                Rect.makeWH(small.width.toFloat(), small.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                sampling,
                null,
                true,
            )
            canvas.restore()
        }
        blurred.close()
        frame.close()
    }

    /** A Liquid Glass panel: the sharp frame and its blur through the same shader Haze draws the preview with. */
    private fun drawGlass(canvas: Canvas, frame: Image, blurred: Image, b: BackdropPrim) {
        val u = GlassShader.uniforms(b, width.toFloat(), height.toFloat())
        val builder = RuntimeShaderBuilder(glass)
        for ((name, v) in u.floats) {
            when (v.size) {
                1 -> builder.uniform(name, v[0])
                2 -> builder.uniform(name, v[0], v[1])
                4 -> builder.uniform(name, v[0], v[1], v[2], v[3])
            }
        }
        for ((name, v) in u.colors) builder.uniform(name, v[0], v[1], v[2], v[3])
        builder.child("frame", frame.makeShader())
        // The blur is a quarter size: scaled up to frame pixels, where the shader samples it. Glass without frost
        // bends the sharp picture.
        builder.child(
            "content",
            if (b.blur < SHARP_BELOW) frame.makeShader()
            else
                blurred.makeShader(
                    FilterTileMode.CLAMP,
                    FilterTileMode.CLAMP,
                    sampling,
                    Matrix33.makeScale(BLUR_DOWNSCALE.toFloat(), BLUR_DOWNSCALE.toFloat()),
                ),
        )
        val paint = Paint()
        paint.shader = builder.makeShader()
        canvas.drawRect(Rect.makeLTRB(b.box.left, b.box.top, b.box.right, b.box.bottom), paint)
        paint.close()
        builder.close()
    }
}

/** The blur is computed on a frame this many times smaller each way. */
private const val BLUR_DOWNSCALE = 4

/** Haze's blur radius and a Gaussian sigma are not the same number; this keeps the two looking alike. */
private const val SIGMA_PER_RADIUS = 0.6f

/** A glass blur under this many pixels is no frost: the glass samples the sharp frame. */
private const val SHARP_BELOW = 1f
