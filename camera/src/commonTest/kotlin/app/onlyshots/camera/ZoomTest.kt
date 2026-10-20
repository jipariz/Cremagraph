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

    @Test
    fun theLensesAreTheStopsTheCameraAppOffers() {
        // An ultra-wide (CameraX's least zoom), the main lens reported again, and a telephoto at 2.94.
        assertEquals(listOf(0.6f, 1f, 3f), lensStops(listOf(0.55f, 1.02f, 2.94f), min = 0.6f, max = 10f))
    }

    @Test
    fun aLensPastTheZoomTheCameraAllowsIsLeftOut() {
        assertEquals(listOf(1f, 2f), lensStops(listOf(2f, 5f), min = 1f, max = 4f))
    }

    @Test
    fun aCameraThatNamesNoLensesHasTheMainOne() {
        assertEquals(listOf(1f), lensStops(emptyList(), min = 1f, max = 1f))
        assertEquals(listOf(0.5f, 1f), lensStops(listOf(0.5f), min = 0.5f, max = 10f))
    }
}
