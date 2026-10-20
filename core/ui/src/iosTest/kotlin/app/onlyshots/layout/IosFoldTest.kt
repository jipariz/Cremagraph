package app.onlyshots.layout

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iPhone Duo's fold, as the host reports it, through the one layout decision. */
class IosFoldTest {
    private fun layout(w: Float, h: Float, fold: IosFold?, density: Float = 3f): WindowLayout {
        val info = foldedInfo(WindowAdaptiveInfo(WindowSizeClass.compute(w, h), Posture()), fold, density)
        return windowLayout(info, w, h, density)
    }

    @Test
    fun closedTheOuterDisplayIsAPhone() {
        assertEquals(LayoutKind.Phone, layout(466f, 678f, fold = null).kind)
    }

    @Test
    fun fullyOpenTheInnerDisplaySplitsAtTheFold() {
        // Flat, the fold divides nothing by itself, but the design still gives each side a page.
        val l = layout(951f, 669f, IosFold(partiallyOpen = false, 475f, 0f, 476f, 669f))
        assertEquals(LayoutKind.Wide, l.kind)
        assertTrue(l.fold)
        assertEquals(475f, l.splitDp, 0.01f)
    }

    @Test
    fun halfOpenOnItsSideItStandsLikeATentAndStacks() {
        // Turned so the fold lies across: the top half stands, the bottom half rests on the table.
        val l = layout(669f, 951f, IosFold(partiallyOpen = true, 0f, 475f, 669f, 476f))
        assertEquals(LayoutKind.Stacked, l.kind)
        assertEquals(475f, l.splitDp, 0.01f)
        assertTrue(!l.onItsSide, "taller than wide: the halves stand one above the other")
    }

    @Test
    fun halfOpenLikeABookItSplitsAtTheFold() {
        val l = layout(951f, 669f, IosFold(partiallyOpen = true, 475f, 0f, 476f, 669f))
        assertEquals(LayoutKind.Wide, l.kind)
        assertEquals(475f, l.splitDp, 0.01f)
    }
}
