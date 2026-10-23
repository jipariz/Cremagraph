package app.onlyshots.record

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.VideoFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraFrameTest {
    private val portrait = VideoFormat.PORTRAIT_9_16

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 1e-4f, "expected $expected, was $actual")

    @Test
    fun aFrameOfThePanesShapeIsShownWhole() {
        val frame = cameraFrame(360f, 640f, portrait.ratio)
        assertNear(360f, frame.width)
        assertNear(640f, frame.height)
        assertNear(1f, frame.shown.w)
        assertNear(1f, frame.shown.h)
    }

    @Test
    fun aLongerPaneCutsTheFramesSidesOff() {
        // A 9:16 frame covering a 9:20 pane: as tall as the pane, wider than it, centred.
        val frame = cameraFrame(360f, 800f, portrait.ratio)
        assertNear(800f, frame.height)
        assertNear(450f, frame.width)
        assertNear(0.8f, frame.shown.w)
        assertNear(1f, frame.shown.h)
        assertNear(0.1f, frame.shown.x)
        assertNear(0f, frame.shown.y)
    }

    @Test
    fun aWiderPaneCutsTheFramesTopAndBottomOff() {
        val frame = cameraFrame(800f, 300f, VideoFormat.LANDSCAPE_16_9.ratio)
        assertNear(800f, frame.width)
        assertNear(450f, frame.height)
        assertNear(1f, frame.shown.w)
        assertNear(300f / 450f, frame.shown.h)
        assertNear((1f - 300f / 450f) / 2f, frame.shown.y)
    }

    @Test
    fun theGraphKeepsAMarginInsideWhatIsShown() {
        val shown = NormRect(0.1f, 0f, 0.8f, 1f)
        val bounds = context(portrait) { graphBounds(shown) }
        val mx = NormRect.MARGIN_REF / portrait.refW
        val my = NormRect.MARGIN_REF / portrait.refH
        assertNear(0.1f + mx, bounds.x)
        assertNear(my, bounds.y)
        assertNear(0.8f - 2 * mx, bounds.w)
        assertNear(1f - 2 * my, bounds.h)
    }

    @Test
    fun aSliverOfWindowKeepsTheWholeFrame() {
        val bounds = context(portrait) { graphBounds(NormRect(0.45f, 0f, 0.1f, 1f)) }
        assertEquals(NormRect.Frame, bounds)
    }

    @Test
    fun whatIsShownInRootPx() {
        val shown = NormRect(0.1f, 0.2f, 0.8f, 0.5f)
        assertEquals(Rect(110f, 60f, 190f, 160f), shownIn(Rect(100f, 20f, 200f, 220f), shown))
    }

    @Test
    fun thePaddingBringsTheFrameInToWhatIsShown() {
        val padding = CameraFrame(450f, 800f, NormRect(0.1f, 0f, 0.8f, 1f)).shownPadding()
        assertNear(45f, padding.calculateLeftPadding(LayoutDirection.Ltr).value)
        assertNear(45f, padding.calculateRightPadding(LayoutDirection.Ltr).value)
        assertEquals(0.dp, padding.calculateTopPadding())
        assertEquals(0.dp, padding.calculateBottomPadding())
    }

    @Test
    fun aGraphOutsideIsMovedInOnlyBeforeATakeWithData() {
        val bounds = NormRect(0.1f, 0.1f, 0.8f, 0.8f)
        val out = NormRect(0f, 0.5f, 0.5f, 0.2f)
        assertTrue(graphOutside(out, bounds, recording = false, hasData = true))
        assertFalse(graphOutside(out, bounds, recording = true, hasData = true))
        assertFalse(graphOutside(out, bounds, recording = false, hasData = false))
        assertFalse(graphOutside(NormRect(0.2f, 0.5f, 0.5f, 0.2f), bounds, recording = false, hasData = true))
        // What moving it between frames rounds off is no reason to move it.
        assertFalse(graphOutside(NormRect(0.098f, 0.5f, 0.5f, 0.2f), bounds, recording = false, hasData = true))
    }

    @Test
    fun lumaIsSmoothedFromItsFirstSample() {
        assertEquals(0.8f, smoothLuma(null, 0.8f))
        assertNear(0.6f, smoothLuma(0.4f, 0.8f))
    }
}
