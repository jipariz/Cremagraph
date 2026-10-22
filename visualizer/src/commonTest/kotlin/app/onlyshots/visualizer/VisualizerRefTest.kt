package app.onlyshots.visualizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualizerRefTest {
    private val id = "832f7109-46d5-47f5-86bc-5349de2957d8"

    @Test
    fun fourLettersAreAShareCode() {
        assertEquals(VisualizerRef.Code("ABCD"), VisualizerRef.parse("ABCD"))
        assertEquals(VisualizerRef.Code("ABCD"), VisualizerRef.parse("  abcd \n"))
    }

    @Test
    fun aLinkOrIdIsTheShot() {
        assertEquals(VisualizerRef.Id(id), VisualizerRef.parse(id))
        assertEquals(VisualizerRef.Id(id), VisualizerRef.parse("https://visualizer.coffee/shots/$id"))
        assertEquals(VisualizerRef.Id(id), VisualizerRef.parse("visualizer.coffee/shots/${id.uppercase()}?tab=chart"))
        assertEquals(VisualizerRef.Id(id), VisualizerRef.parse("https://visualizer.coffee/api/shots/$id/download"))
    }

    @Test
    fun anythingElseIsNeither() {
        assertNull(VisualizerRef.parse(""))
        assertNull(VisualizerRef.parse("ABC"))
        assertNull(VisualizerRef.parse("AB1D"))
        assertNull(VisualizerRef.parse("ABCDE"))
        assertNull(VisualizerRef.parse("https://visualizer.coffee/shots"))
    }
}
