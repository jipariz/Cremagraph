package app.onlyshots.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomTest {
    @Test
    fun theRangeTheLensesCoverUpToTheCap() {
        assertEquals(1f..8f, zoomRange(1f, 8f))
        assertEquals(0.5f..MAX_ZOOM, zoomRange(0.5f, 100f))
    }

    @Test
    fun aCameraThatReportsNothingUsableGetsASinglePoint() {
        assertEquals(1f..1f, zoomRange(1f, 0f))
    }
}
