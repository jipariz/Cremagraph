package app.onlyshots.video

import app.onlyshots.core.model.Reframe
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** The exporters' reframe lands the picture where the preview's graphicsLayer, and [Reframe.map], put it. */
class SkiaFrameDrawerTest {
    private val w = 180
    private val h = 320

    /** A black frame with a red square centred at ([fx], [fy]) of it. */
    private fun frame(fx: Float, fy: Float) =
        Surface.makeRasterN32Premul(w, h).run {
            canvas.clear(Color.BLACK)
            val paint = Paint().apply { color = Color.RED }
            canvas.drawRect(Rect.makeXYWH(fx * w - 3, fy * h - 3, 6f, 6f), paint)
            makeImageSnapshot()
        }

    /** The centre of the red pixels in [out], as fractions of the frame. */
    private fun redCentre(out: Surface): Pair<Float, Float> {
        val bitmap = org.jetbrains.skia.Bitmap().apply { allocN32Pixels(w, h) }
        out.readPixels(bitmap, 0, 0)
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (y in 0 until h) for (x in 0 until w) {
            val c = bitmap.getColor(x, y)
            if (Color.getR(c) > 128 && Color.getG(c) < 64) {
                sx += x + 0.5
                sy += y + 0.5
                n++
            }
        }
        check(n > 0) { "no red in the output" }
        return (sx / n / w).toFloat() to (sy / n / h).toFloat()
    }

    private fun draw(reframe: Reframe, fx: Float, fy: Float): Pair<Float, Float> {
        val out = Surface.makeRasterN32Premul(w, h)
        SkiaFrameDrawer(w, h, rotation = 0, reframe = reframe).draw(frame(fx, fy), out, overlay = null)
        return redCentre(out)
    }

    private fun assertNear(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        // Within a pixel.
        assertEquals((expected.first * w).roundToInt().toFloat(), (actual.first * w), 1.5f)
        assertEquals((expected.second * h).roundToInt().toFloat(), (actual.second * h), 1.5f)
    }

    @Test
    fun atZoomOneThePictureIsUntouched() {
        assertNear(0.4f to 0.7f, draw(Reframe(), 0.4f, 0.7f))
    }

    @Test
    fun aZoomedFrameMovesThePictureAsTheModelSays() {
        val r = Reframe(zoom = 2f, x = 0.35f, y = 0.6f)
        assertNear(r.map(0.4f, 0.7f), draw(r, 0.4f, 0.7f))
    }
}
