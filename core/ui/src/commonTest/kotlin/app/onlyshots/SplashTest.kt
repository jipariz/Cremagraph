package app.onlyshots

import androidx.compose.ui.geometry.Offset
import app.onlyshots.core.designsystem.component.Splash
import app.onlyshots.core.designsystem.component.SplashTimeline
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The splash's timeline, as design/splash/SPEC.md and motion.py have it. */
class SplashTest {
    private val app = SplashTimeline.app

    @Test
    fun theTileWipesInFromItsLeftEdge() {
        assertEquals(0f, Splash.pose(0f).wipe, 0.001f)
        assertTrue(Splash.pose(130f).wipe in 0.5f..0.99f)
        assertEquals(1f, Splash.pose(app.wipe.endInclusive).wipe, 0.001f)
    }

    @Test
    fun thePenLandsOnTheTilesEdgeAndOvershoots() {
        assertEquals(0f, Splash.pose(app.land.start).headScale, 0.001f)
        // Out-back: it swells past its size on the way in, then settles.
        assertTrue((220..340).any { Splash.pose(it.toFloat()).headScale > 1.02f })
        assertEquals(1f, Splash.pose(app.land.endInclusive).headScale, 0.001f)
        // Landed, it sits at the start of the trace, half a head ahead of it.
        val landed = Splash.pose(app.land.endInclusive)
        assertTrue(landed.head.x < Splash.T0 + Splash.HEAD)
        assertEquals(Splash.FLAT_Y, landed.head.y, 0.5f)
    }

    @Test
    fun thePlotRunsTheTraceToThePenThenTheStemDrops() {
        assertEquals(0f, Splash.pose(app.draw.start).draw, 0.0001f)
        val plotted = Splash.pose(app.draw.endInclusive)
        assertEquals(1f, plotted.draw, 0.0001f)
        assertEquals(Offset(Splash.PEN_X, Splash.PEN_Y), plotted.head.round())
        assertEquals(0f, plotted.stem, 0.0001f)
        assertEquals(1f, Splash.pose(app.stem.endInclusive).stem, 0.0001f)
    }

    @Test
    fun theHeadTicksOnceAndTheMarkIsTheLogoAgain() {
        val swell = (1120..1400 step 5).maxOf { Splash.pose(it.toFloat()).headScale }
        assertEquals(1.2f, swell, 0.01f)
        val after = Splash.pose(app.tick.endInclusive)
        assertEquals(Splash.finished, after.copy(head = after.head.round()))
    }

    @Test
    fun androidsSystemSplashDrawsWithinItsSecond() {
        val android = SplashTimeline.android
        assertTrue(android.drawn <= 1000f)
        val drawn = Splash.pose(android.drawn, android)
        assertEquals(1f, drawn.draw, 0.0001f)
        assertEquals(1f, drawn.stem, 0.0001f)
    }

    private fun Offset.round() =
        Offset(
            if (abs(x - Splash.PEN_X) < 0.05f) Splash.PEN_X else x,
            if (abs(y - Splash.PEN_Y) < 0.05f) Splash.PEN_Y else y,
        )
}
