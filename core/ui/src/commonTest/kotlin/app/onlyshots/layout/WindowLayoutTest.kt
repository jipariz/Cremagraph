package app.onlyshots.layout

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One case per row of the handover's adaptive table, on the adaptive library's window info. */
class WindowLayoutTest {
    private fun layout(w: Float, h: Float, posture: Posture = Posture()) =
        windowLayout(WindowAdaptiveInfo(WindowSizeClass.compute(w, h), posture), w, h)

    /** A hinge across the window: [vertical] runs top to bottom; bounds in px at density 1. */
    private fun hinge(vertical: Boolean, start: Float, end: Float, w: Float, h: Float, flat: Boolean) =
        HingeInfo(
            bounds = if (vertical) Rect(start, 0f, end, h) else Rect(0f, start, w, end),
            isFlat = flat,
            isVertical = vertical,
            isSeparating = true,
            isOccluding = true,
        )

    @Test
    fun aPhoneKeepsOneColumn() {
        assertEquals(LayoutKind.Phone, layout(390f, 844f).kind)
    }

    @Test
    fun aTabletInPortraitSplits() {
        val l = layout(800f, 1280f)
        assertEquals(LayoutKind.Wide, l.kind)
        assertEquals(560f, l.splitDp)
        assertEquals(2, l.directive.maxHorizontalPartitions, "two panes on a medium width")
    }

    @Test
    fun aTabletInLandscapeAndTheDesktopSplitWider() {
        val l = layout(1280f, 800f)
        assertEquals(LayoutKind.Wide, l.kind)
        assertEquals(1280f * 0.62f, l.splitDp, 0.01f)
        assertEquals(LayoutKind.Wide, layout(1280f, 860f).kind, "the desktop editor's window")
    }

    @Test
    fun aBookFoldableSplitsAtItsHinge() {
        val l = layout(1600f, 1000f, Posture(false, listOf(hinge(true, 790f, 810f, 1600f, 1000f, flat = true))))
        assertEquals(LayoutKind.Wide, l.kind)
        assertEquals(790f, l.splitDp)
        assertEquals(20f, l.hingeDp)
    }

    @Test
    fun aBookOpenFlatSplitsAtItsFoldThoughItSeparatesNothing() {
        val flat =
            hinge(true, 790f, 810f, 1600f, 1000f, flat = true).let {
                HingeInfo(it.bounds, isFlat = true, isVertical = true, isSeparating = false, isOccluding = false)
            }
        val l = layout(1600f, 1000f, Posture(false, listOf(flat)))
        assertEquals(LayoutKind.Wide, l.kind)
        assertTrue(l.fold)
        assertEquals(790f, l.splitDp)
    }

    @Test
    fun aTabletWithoutAFoldKeepsItsOwnSplit() {
        val l = layout(1280f, 800f)
        assertEquals(LayoutKind.Wide, l.kind)
        assertFalse(l.fold)
    }

    @Test
    fun onItsSideABookStacksWiderThanTall() {
        val l = layout(1000f, 840f, Posture(true, listOf(hinge(false, 410f, 430f, 1000f, 840f, flat = false))))
        assertEquals(LayoutKind.Stacked, l.kind)
        assertTrue(l.onItsSide)
    }

    @Test
    fun aHalfOpenBookSplitsAtItsHingeToo() {
        // The iPhone Duo's inner display held like a book, half open: the fold divides the two panes.
        val l = layout(951f, 669f, Posture(false, listOf(hinge(true, 475f, 476f, 951f, 669f, flat = false))))
        assertEquals(LayoutKind.Wide, l.kind)
        assertEquals(475f, l.splitDp)
    }

    @Test
    fun aHalfOpenFoldableStacksAtItsHinge() {
        // Tabletop on a book, flex mode on a flip: the same layout from the same rule.
        val l = layout(840f, 1000f, Posture(true, listOf(hinge(false, 480f, 500f, 840f, 1000f, flat = false))))
        assertEquals(LayoutKind.Stacked, l.kind)
        assertEquals(480f, l.splitDp)
        assertEquals(20f, l.hingeDp)
    }

    @Test
    fun aFlipCoverScreenIsTiny() {
        assertEquals(LayoutKind.Tiny, layout(360f, 300f).kind)
    }

    @Test
    fun aPhoneInLandscapeGetsTwoPanes() {
        assertEquals(LayoutKind.Wide, layout(844f, 390f).kind)
    }

    @Test
    fun aHingeReportedAtTheEdgeNeverStarvesAPane() {
        val near = layout(1600f, 1000f, Posture(false, listOf(hinge(true, 20f, 40f, 1600f, 1000f, flat = true))))
        assertEquals(MIN_PANE_DP, near.splitDp)
        val far = layout(1600f, 1000f, Posture(false, listOf(hinge(true, 1590f, 1600f, 1600f, 1000f, flat = true))))
        assertTrue(far.splitDp <= 1600f - MIN_PANE_DP - far.hingeDp, "the second pane was squeezed out: $far")
    }

    @Test
    fun aFoldedPhoneWithNoSeparatingHingeIsJustAPhone() {
        // Nothing separating on a closed book, and that is the right answer: one column.
        assertEquals(LayoutKind.Phone, layout(390f, 844f, Posture(false, emptyList())).kind)
    }
}
