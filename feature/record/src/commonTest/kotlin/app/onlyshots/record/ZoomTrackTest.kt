package app.onlyshots.record

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomTrackTest {
    @Test
    fun theTrackIsLogarithmic() {
        // 0.5× to 8×: four doublings, so 1× sits a quarter of the way up and 2× halfway.
        assertEquals(0.25f, zoomFraction(1f, 0.5f, 8f), 1e-4f)
        assertEquals(0.5f, zoomFraction(2f, 0.5f, 8f), 1e-4f)
        assertEquals(2f, zoomAt(0.5f, 0.5f, 8f), 1e-4f)
    }

    @Test
    fun theEndsAndBeyondClamp() {
        assertEquals(0f, zoomFraction(0.1f, 1f, 4f))
        assertEquals(1f, zoomFraction(9f, 1f, 4f))
        assertEquals(4f, zoomAt(2f, 1f, 4f), 1e-4f)
        assertEquals(1f, zoomAt(0.3f, 1f, 1f))
    }

    @Test
    fun labelsDropAZeroDecimal() {
        assertEquals("1×", zoomLabel(1f))
        assertEquals("0.5×", zoomLabel(0.5f))
        assertEquals("2.4×", zoomLabel(2.43f))
        assertEquals("10×", zoomLabel(9.98f))
    }
}
