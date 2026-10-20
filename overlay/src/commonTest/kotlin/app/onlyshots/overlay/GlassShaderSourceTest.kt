package app.onlyshots.overlay

import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.core.model.style.GlassSettings
import app.onlyshots.core.model.style.PanelStyle
import app.onlyshots.overlay.glass.GlassShader
import app.onlyshots.overlay.glass.MAX_DISPLACEMENT
import app.onlyshots.overlay.glass.toHaze
import app.onlyshots.overlay.scene.BackdropPrim
import app.onlyshots.overlay.scene.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassShaderSourceTest {
    private val prim =
        BackdropPrim(
            Box(10f, 20f, 210f, 120f),
            16f,
            20f,
            GlassSettings(refraction = 0.3f, curve = 0.4f, edge = 0.05f, contrast = 1.2f),
            rgba(255, 255, 255, 0.14f),
        )

    @Test
    fun everyUniformIsDeclaredInBothDialects() {
        val u = GlassShader.uniforms(prim, 1080f, 1920f)
        for (name in u.floats.keys + u.colors.keys + listOf("frame", "content")) {
            assertTrue(Regex("uniform\\s+\\w+\\s+$name;").containsMatchIn(GlassShader.sksl), "SkSL lacks $name")
            assertTrue(Regex("uniform\\s+\\w+\\s+$name;").containsMatchIn(GlassShader.glsl), "GLSL lacks $name")
        }
    }

    @Test
    fun theGlslHasNoSkslLeftInIt() {
        val glsl = GlassShader.glsl
        for (sksl in listOf("uniform shader", ".eval(", "float2", "layout(color)", "vec4 main(vec2")) {
            assertFalse(sksl in glsl, "GLSL still has $sksl")
        }
        assertTrue("void main()" in glsl)
        assertFalse(Regex("surfaceProfile\\s*==\\s*\\d+(?![.\\d])").containsMatchIn(glsl), "int compared to a float")
    }

    @Test
    fun theDefaultsAreTheLiquidDemos() {
        assertEquals(
            GlassSettings(
                frost = 0f,
                refraction = 0.25f,
                curve = 0.25f,
                edge = 0f,
                saturation = 1f,
                corner = 25f,
                dispersion = 0f,
                contrast = 1f,
            ),
            GlassSettings(),
        )
    }

    @Test
    fun theLensIsAShareOfTheShorterSide() {
        val u = GlassShader.uniforms(prim, 1080f, 1920f)
        // The panel is 200 × 100: the lens reaches 30 px in, displaces up to 40 px, the lit rim is 5 px wide.
        assertEquals(30f, u.floats.getValue("refractionHeight")[0], 1e-3f)
        assertEquals(40f, u.floats.getValue("refractionScale")[0], 1e-3f)
        assertEquals(5f, u.floats.getValue("edgeSoftness")[0], 1e-3f)
        assertEquals(16f, u.floats.getValue("cornerRadii")[0])
        assertEquals(-1f, u.floats.getValue("edgeRefractionWidth")[0])
        // Liquid's contrast is neutral at 1, Haze's at 0.
        assertEquals(0.2f, u.floats.getValue("contrast")[0], 1e-4f)
        assertEquals(0.14f, u.colors.getValue("tintColor")[3], 0.01f)
        assertEquals(MAX_DISPLACEMENT, GlassSettings(curve = 1f).toHaze(2000f, 1000f).refractionDisplacement)
    }

    @Test
    fun noEdgeMeansNoLitRim() {
        val h = GlassSettings().toHaze(200f, 100f)
        assertEquals(0f, h.specularIntensity)
        assertEquals(0f, h.ambient)
    }

    @Test
    fun aStyleSavedWithTheOldHazeFieldsStillOpens() {
        val old = """{"material":"GLASS","glass":{"base":"CLEAR","refraction":0.85,"depth":56.0,"light":"TOP_LEFT"}}"""
        val panel = SidecarCodec.json.decodeFromString(PanelStyle.serializer(), old)
        assertEquals(GlassSettings(refraction = 0.85f), panel.glass)
    }
}
