package app.onlyshots.overlay

import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadabilityTest {
    /** Milk values on clear glass: fine over a dark frame, hard over a bright cup. */
    private val clearGlass =
        GraphStyles.Gaggiuino.copy(
            id = "u-glass",
            panel = GraphStyles.Gaggiuino.panel.copy(material = Material.GLASS, color = rgb(0xFFFFFF), opacity = 0.10f),
            cells =
                GraphStyles.Gaggiuino.cells.copy(
                    look = CellLook.PLAIN,
                    values = Reading.entries.associateWith { rgb(0xF2E6DC) },
                ),
        )

    @Test
    fun builtInsReadOverADarkFrame() {
        for (s in GraphStyles.all) assertNull(s.readabilityIssue(0.05f), s.id)
    }

    @Test
    fun lightValuesOnClearGlassOverABrightCupAreFlagged() {
        assertNull(clearGlass.readabilityIssue(0.02f))
        val issue = assertNotNull(clearGlass.readabilityIssue(0.7f))
        assertTrue(issue.ratio < VALUE_CONTRAST)
    }

    @Test
    fun fixItForMeMakesItReadable() {
        val fixed = clearGlass.fixReadability(0.7f)
        assertNull(fixed.readabilityIssue(0.7f))
        assertEquals(Material.GLASS, fixed.panel.material)
    }

    @Test
    fun withoutAPanelTheFixIsToAdaptToTheVideo() {
        val bare =
            clearGlass.copy(
                panel = clearGlass.panel.copy(material = Material.NONE),
                text = clearGlass.text.copy(effect = null),
            )
        assertNotNull(bare.readabilityIssue(0.8f))
        val fixed = bare.fixReadability(0.8f)
        assertTrue(fixed.adapt.enabled)
        assertNull(fixed.readabilityIssue(0.8f))
    }

    @Test
    fun towardBlackKeepsAlpha() {
        assertEquals(0x80000000.toInt(), 0x80FFFFFF.toInt().towardBlack(1f))
        assertEquals(rgb(0x7F7F7F), rgb(0xFFFFFF).towardBlack(0.5f))
    }
}
