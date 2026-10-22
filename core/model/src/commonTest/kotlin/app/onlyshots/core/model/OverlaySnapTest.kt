package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverlaySnapTest {
    private val pull = 0.02f

    @Test
    fun nearTheBottomSafeLineItSnapsAboveTheCaptions() {
        context(VideoFormat.PORTRAIT_9_16) {
            val free = NormRect(0.3f, 1f - OverlaySnap.SAFE_BOTTOM - 0.2f + 0.01f, 0.4f, 0.2f)
            val s = OverlaySnap.snap(free, pull, pull)
            assertEquals(OverlaySnap.Place.BOTTOM_ABOVE_CAPTIONS, s.vertical?.place)
            assertEquals(1f - OverlaySnap.SAFE_BOTTOM - 0.2f, s.rect.y, 1e-6f)
            assertEquals(OverlaySnap.Place.CENTRE, s.horizontal?.place)
            assertEquals(0.3f, s.rect.x, 1e-6f)
        }
    }

    @Test
    fun pastThePullDistanceNothingSnaps() {
        context(VideoFormat.PORTRAIT_9_16) {
            val free = NormRect(0.21f, 0.33f, 0.4f, 0.2f)
            val s = OverlaySnap.snap(free, pull, pull)
            assertNull(s.vertical)
            assertNull(s.horizontal)
            assertEquals(free, s.rect)
        }
    }

    @Test
    fun theSideMarginsAndTheHeaderLineAreTargets() {
        context(VideoFormat.PORTRAIT_9_16) {
            val s = OverlaySnap.snap(NormRect(0.04f, 0.13f, 0.5f, 0.2f), pull, pull)
            assertEquals(OverlaySnap.Place.LEFT, s.horizontal?.place)
            assertEquals(OverlaySnap.MARGIN, s.rect.x, 1e-6f)
            assertEquals(OverlaySnap.Place.TOP_BELOW_HEADER, s.vertical?.place)
            assertEquals(OverlaySnap.SAFE_TOP, s.rect.y, 1e-6f)
        }
    }
}
