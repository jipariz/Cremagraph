package app.onlyshots.layout

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tablets of every common size, both ways up, with the editor's chrome and each format. */
class PanePlacementTest {
    private val tall = 9f / 16f
    private val feed = 4f / 5f
    private val square = 1f
    private val wide = 16f / 9f

    /** The editor: the top bar and the timeline around the picture, a 360 dp panel under them. */
    private fun edit(w: Float, h: Float, ratio: Float) = placePanel(w, h, ratio, chromeDp = 236f, belowPanelDp = 360f)

    @Test
    fun aLargeUprightTabletKeepsATallVideoBesideThePanel() {
        // 636 × 1130 beside, against 433 × 770 above a panel.
        assertEquals(PanePlacement.Side, edit(1024f, 1366f, tall))
        assertEquals(PanePlacement.Below, edit(1024f, 1366f, square))
    }

    @Test
    fun aTabletOnItsSideKeepsThePanelBeside() {
        for ((w, h) in listOf(960f to 552f, 1280f to 728f, 1366f to 1024f)) {
            for (ratio in listOf(tall, square, wide)) {
                assertEquals(PanePlacement.Side, edit(w, h, ratio), "$w × $h at $ratio")
            }
        }
    }

    @Test
    fun theEditorStacksAllButATallVideoOnAnUprightTablet() {
        assertEquals(PanePlacement.Side, edit(800f, 1208f, tall))
        assertEquals(PanePlacement.Below, edit(800f, 1208f, feed))
        assertEquals(PanePlacement.Below, edit(800f, 1208f, square))
        assertEquals(PanePlacement.Below, edit(800f, 1208f, wide))
        assertEquals(PanePlacement.Side, edit(1280f, 728f, wide))
    }

    @Test
    fun thePanelBelowTakesTheHeightAWidePictureLeaves() {
        // 16:9 across 800 dp is 450 tall: the editor's panel gets the rest of the 1208 − 236.
        assertEquals(522f, belowPanelHeightDp(800f, 1208f, wide, chromeDp = 236f, minPanelDp = 360f))
        // A tall picture fills the height it is given: the panel keeps its least.
        assertEquals(360f, belowPanelHeightDp(800f, 1208f, tall, chromeDp = 236f, minPanelDp = 360f))
    }

    @Test
    fun theSidePanelNarrowsOnAMediumWindow() {
        assertEquals(380f, sidePanelWidthDp(1280f))
        assertEquals(340f, sidePanelWidthDp(800f))
    }

    @Test
    fun anUnsizedPanelIsHalfAMediumWindowAndTheDeskPanelOnAnExpandedOne() {
        assertEquals(390f, defaultPanelWidthDp(800f, gapDp = 20f))
        assertEquals(380f, defaultPanelWidthDp(1280f, gapDp = 20f))
    }

    @Test
    fun aFrameFitsByItsTighterSide() {
        assertEquals(800f to 450f, fitRatio(800f, 864f, wide))
        assertEquals(486f to 864f, fitRatio(800f, 864f, tall))
        assertEquals(0f to 0f, fitRatio(0f, 100f, tall))
    }
}
