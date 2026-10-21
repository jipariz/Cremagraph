package app.onlyshots.record

import kotlin.test.Test
import kotlin.test.assertEquals

class LensPickerTest {
    private val lenses = listOf(0.6f, 1f, 3f)

    @Test
    fun theLitStopIsTheLongestLensTheZoomHasReached() {
        assertEquals(0, activeLens(0.6f, lenses))
        assertEquals(1, activeLens(1f, lenses))
        assertEquals(1, activeLens(2.4f, lenses), "a pinch past the main lens")
        assertEquals(2, activeLens(3f, lenses))
        assertEquals(0, activeLens(0.5f, lenses), "under the widest, still the widest")
    }

    @Test
    fun stopsNameTheirLensAndTheLitOneTheZoom() {
        assertEquals("0.6", lensLabel(0.6f))
        assertEquals("2", lensLabel(2f))
        assertEquals("1.0×", zoomLabel(1f))
        assertEquals("2.4×", zoomLabel(2.43f))
    }
}
