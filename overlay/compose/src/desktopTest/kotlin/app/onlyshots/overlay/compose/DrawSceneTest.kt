package app.onlyshots.overlay.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.onlyshots.overlay.Dash
import app.onlyshots.overlay.scene.Box
import app.onlyshots.overlay.scene.OverlayScene
import app.onlyshots.overlay.scene.PolylinePrim
import app.onlyshots.overlay.scene.Prim
import app.onlyshots.overlay.scene.smoothRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** drawScene into an offscreen bitmap: what the painter itself does to the scene, checked pixel by pixel. */
class DrawSceneTest {
    private val white = 0xFFFFFFFF.toInt()

    private fun render(vararg prims: Prim, w: Int = 100, h: Int = 40): PixelMap {
        val bitmap = ImageBitmap(w, h)
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
            drawScene(OverlayScene(Box(0f, 0f, w.toFloat(), h.toFloat()), prims.toList()), measurer, FontFamily.Default)
        }
        return bitmap.toPixelMap()
    }

    private fun PixelMap.alphaAt(x: Int, y: Int) = this[x, y].alpha

    @Test
    fun aHairlineLandsOnOnePixelRow() {
        // 1 px line at y = 10.3: unsnapped it would smear over rows 9 and 10; snapped it fills row 10 alone.
        val px = render(PolylinePrim(floatArrayOf(10f, 10.3f, 90f, 10.3f), white, 1f))
        assertTrue(px.alphaAt(50, 10) > 0.95f, "row 10: ${px.alphaAt(50, 10)}")
        assertTrue(px.alphaAt(50, 9) < 0.05f, "row 9: ${px.alphaAt(50, 9)}")
        assertTrue(px.alphaAt(50, 11) < 0.05f, "row 11: ${px.alphaAt(50, 11)}")
    }

    @Test
    fun dashesKeepTheirGaps() {
        // 6 on, 6 off, 4 px wide: with butt caps x = 9 (inside the first gap, 6..12) stays empty; round caps would
        // reach 2 px into it and nearly close a gap this short.
        val px = render(PolylinePrim(floatArrayOf(0f, 20f, 100f, 20f), white, 4f, Dash(6f, 6f)))
        assertTrue(px.alphaAt(3, 20) > 0.9f)
        assertTrue(px.alphaAt(9, 20) < 0.05f, "gap at 9: ${px.alphaAt(9, 20)}")
        assertTrue(px.alphaAt(15, 20) > 0.9f)
    }

    @Test
    fun aSmoothedCurveStaysInsideItsPlot() {
        // A spike clamped to the plot's top (y 8): nothing is drawn above it but the stroke's own half width.
        val run = smoothRun(floatArrayOf(0f, 30f, 25f, 8f, 50f, 30f, 75f, 8f, 100f, 30f), top = 8f, bottom = 32f)
        val px = render(PolylinePrim(run, white, 2f))
        for (x in 0 until 100) for (y in 0 until 6) assertEquals(0f, px.alphaAt(x, y), "($x, $y)")
    }
}
