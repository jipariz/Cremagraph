package app.onlyshots.overlay.compose

import app.onlyshots.core.model.style.GlassSettings
import app.onlyshots.overlay.glass.GlassShader
import app.onlyshots.overlay.scene.BackdropPrim
import app.onlyshots.overlay.scene.Box
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

/** The exporters' Liquid Glass compiles in Skia and draws: changes the inside of the panel, leaves the outside. */
class GlassShaderTest {
    @Test
    fun theSkslCompilesAndOnlyTheInsideChanges() {
        val effect = RuntimeEffect.makeForShader(GlassShader.sksl)
        // A striped frame, so refraction has something to bend.
        val frame = Surface.makeRasterN32Premul(200, 120)
        frame.canvas.clear(Color.BLACK)
        Paint().use { p ->
            p.color = Color.WHITE
            for (x in 0 until 200 step 10) frame.canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, 5f, 120f), p)
        }
        val image = frame.makeImageSnapshot()
        val prim = BackdropPrim(Box(40f, 20f, 160f, 100f), 16f, 8f, GlassSettings(refraction = 0.5f, curve = 0.5f), 0)
        val u = GlassShader.uniforms(prim, 200f, 120f)
        val builder = RuntimeShaderBuilder(effect)
        for ((name, v) in u.floats) {
            when (v.size) {
                1 -> builder.uniform(name, v[0])
                2 -> builder.uniform(name, v[0], v[1])
                4 -> builder.uniform(name, v[0], v[1], v[2], v[3])
            }
        }
        for ((name, v) in u.colors) builder.uniform(name, v[0], v[1], v[2], v[3])
        builder.child("frame", image.makeShader())
        builder.child("content", image.makeShader())
        val out = Surface.makeRasterN32Premul(200, 120)
        Paint().use { p ->
            p.shader = builder.makeShader()
            out.canvas.drawRect(Rect.makeWH(200f, 120f), p)
        }
        val result = out.makeImageSnapshot()
        val before =
            org.jetbrains.skia.Bitmap().also {
                it.allocN32Pixels(200, 120)
                image.readPixels(it)
            }
        val after =
            org.jetbrains.skia.Bitmap().also {
                it.allocN32Pixels(200, 120)
                result.readPixels(it)
            }
        // Outside the panel: the frame as it was.
        for (x in listOf(5, 25, 175, 195)) assertTrue(before.getColor(x, 60) == after.getColor(x, 60), "outside at $x")
        // Inside, near the edge where refraction bends the stripes: something moved.
        val changed = (44 until 60).count { x -> before.getColor(x, 60) != after.getColor(x, 60) }
        assertTrue(changed > 4, "inside changed at only $changed pixels")
    }
}
