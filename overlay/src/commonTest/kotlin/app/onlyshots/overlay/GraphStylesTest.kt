package app.onlyshots.overlay

import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphStylesTest {
    @Test
    fun builtInStyles() {
        assertEquals(listOf("gaggiuino", "metro", "aero", "liquidglass", "minimal"), GraphStyles.all.map { it.id })
        assertTrue(GraphStyles.all.all { it.builtIn })
    }

    @Test
    fun aGaggiuinoGetsGaggiuinoAndEverythingElseMetro() {
        assertEquals("gaggiuino", GraphStyles.resolve("auto", SourceKind.GAGGIUINO).id)
        assertEquals("metro", GraphStyles.resolve("auto", SourceKind.GAGGIMATE).id)
        assertEquals("metro", GraphStyles.resolve("auto", SourceKind.DECENT).id)
        assertEquals("metro", GraphStyles.resolve("auto", SourceKind.SCALE).id)
        assertEquals("metro", GraphStyles.resolve("auto", null).id)
    }

    @Test
    fun explicitStyleWinsOverSource() {
        assertEquals("aero", GraphStyles.resolve("aero", SourceKind.GAGGIUINO).id)
    }

    @Test
    fun oldStyleIdsOpenInTheClosestNewStyle() {
        assertEquals("gaggiuino", GraphStyles.resolve("gweb", null).id)
        assertEquals("gaggiuino", GraphStyles.resolve("gscreen", null).id)
        for (id in listOf("tiles", "gmate", "decentapp", "decent", "decentdark", "wendougee")) {
            assertEquals("metro", GraphStyles.resolve(id, SourceKind.GAGGIUINO).id, id)
        }
        assertEquals("minimal", GraphStyles.resolve("minimal", null).id)
    }

    @Test
    fun unknownIdFallsBackToTheSourceDefault() {
        assertEquals("gaggiuino", GraphStyles.resolve("retired-style", SourceKind.GAGGIUINO).id)
    }

    @Test
    fun aUsersOwnStyleAndTheirDefaultsWin() {
        val mine = GraphStyles.Aero.copy(id = "u-1", name = "Morning glass", builtIn = false)
        assertEquals("u-1", GraphStyles.resolve("u-1", null, custom = listOf(mine)).id)
        assertEquals(
            "u-1",
            GraphStyles.resolve("auto", SourceKind.GAGGIUINO, listOf(mine), mapOf(SourceKind.GAGGIUINO to "u-1")).id,
        )
        // Deleted since: the source default.
        assertEquals("metro", GraphStyles.resolve("u-1", SourceKind.SCALE).id)
    }

    @Test
    fun aVideoKeepsTheCopyOfTheStyleItWasMadeWith() {
        val mine = GraphStyles.Aero.copy(id = "u-1", name = "Morning glass", builtIn = false)
        val overlay = OverlaySettings(styleId = "u-1", style = mine)
        assertEquals(mine, GraphStyles.resolve(overlay, SourceKind.GAGGIUINO))
        // Changed to another style since: the copy is ignored.
        assertEquals("metro", GraphStyles.resolve(overlay.copy(styleId = "metro"), null).id)
    }

    @Test
    fun tokensFromFigma() {
        with(GraphStyles.Gaggiuino) {
            assertEquals(Material.SOLID, panel.material)
            assertEquals(rgb(0x121416), panel.color)
            assertEquals(0.92f, panel.opacity)
            assertEquals(rgb(0x3A90DA), series.pressure)
            assertEquals(rgb(0xFFB53E), series.flow)
            assertEquals(rgb(0xA75C58), series.weight)
            assertEquals(GridMode.BOTH, grid.mode)
            assertEquals(Dash(3f, 3f), grid.dash)
        }
        with(GraphStyles.Metro) {
            assertEquals(24f, panel.blur)
            assertEquals(4f, panel.tiles?.gap)
            assertEquals(CellLook.TILES, cells.look)
            assertEquals(rgb(0xC4006A), cells.tiles[Reading.PRESSURE])
            assertEquals(300, cells.valueWeight)
        }
        with(GraphStyles.Aero) {
            assertEquals(3f, panel.glass.frost)
            assertEquals(4, panel.gradient?.size)
            assertEquals(CellLook.GLOSSY, cells.look)
            assertTrue(text.effect != null)
        }
        with(GraphStyles.Minimal) {
            assertEquals(Material.NONE, panel.material)
            assertEquals(GridMode.NONE, grid.mode)
            assertTrue(adapt.enabled)
            assertEquals(rgb(0x1A1412), adapt.dark?.text)
        }
    }

    @Test
    fun whichStylesBlurTheVideo() {
        assertFalse(GraphStyles.Gaggiuino.blursVideo)
        assertTrue(GraphStyles.Metro.blursVideo)
        assertTrue(GraphStyles.Aero.blursVideo)
        assertFalse(GraphStyles.Minimal.blursVideo)
        assertTrue(GraphStyles.LiquidGlass.blursVideo)
        assertEquals(Material.GLASS, GraphStyles.LiquidGlass.panel.material)
    }

    @Test
    fun everyStyleHasAValueColourForEveryReading() {
        for (s in GraphStyles.all) assertTrue(Reading.entries.all { it in s.cells.values }, s.id)
    }

    @Test
    fun colourHelpers() {
        assertEquals(0xFF3A90DA.toInt(), rgb(0x3A90DA))
        assertEquals(0x80FFFFFF.toInt(), rgba(255, 255, 255, 0.5f))
        assertEquals(0x40FFFFFF, rgb(0xFFFFFF).timesAlpha(0.25f))
        assertEquals(0.5f, rgba(0, 0, 0, 0.5f).alpha, 0.01f)
    }

    @Test
    fun contrastFollowsWcag() {
        assertEquals(21f, contrastRatio(relativeLuminance(rgb(0xFFFFFF)), relativeLuminance(rgb(0x000000))), 0.01f)
        assertEquals(1f, contrastRatio(0.3f, 0.3f), 0.001f)
        // White text on a bright background reads badly; on dark it reads well.
        assertTrue(contrastOf(rgb(0xFFFFFF), 0.8f) < VALUE_CONTRAST)
        assertTrue(contrastOf(rgb(0xFFFFFF), 0.02f) > VALUE_CONTRAST)
    }
}
