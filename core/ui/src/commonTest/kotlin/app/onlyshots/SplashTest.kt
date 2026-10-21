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
    fun thePlayheadStandsUpAtTheTilesEdge() {
        assertEquals(0f, Splash.pose(app.land.start).headScale, 0.001f)
        // Out-back: the head swells past its size on the way in, then settles; the stem stands under it.
        assertTrue((220..380).any { Splash.pose(it.toFloat()).headScale > 1.02f })
        val up = Splash.pose(app.land.endInclusive)
        assertEquals(1f, up.headScale, 0.001f)
        assertEquals(1f, up.stem, 0.001f)
        assertTrue(up.head.x < Splash.T0 + Splash.HEAD)
        assertEquals(Splash.FLAT_Y, up.head.y, 0.5f)
    }

    @Test
    fun theSweepPlaysTheTraceToThePen() {
        assertEquals(0f, Splash.pose(app.draw.start).draw, 0.0001f)
        // Half way the head rides the rise, the trace drawn behind it.
        val mid = Splash.pose((app.draw.start + app.draw.endInclusive) / 2f)
        assertTrue(mid.head.y < Splash.FLAT_Y && mid.head.y > Splash.PEN_Y)
        val swept = Splash.pose(app.draw.endInclusive)
        assertEquals(1f, swept.draw, 0.0001f)
        assertEquals(Offset(Splash.PEN_X, Splash.PEN_Y), swept.head.round())
    }

    @Test
    fun theHeadPulsesOnceWithARingAndTheMarkIsTheLogoAgain() {
        val swell = (1100..1400 step 5).maxOf { Splash.pose(it.toFloat()).headScale }
        assertEquals(1f + Splash.BUMP, swell, 0.01f)
        val ringing = Splash.pose(1200f)
        assertTrue(ringing.ringAlpha > 0f && ringing.ring > Splash.HEAD)
        val after = Splash.pose(app.pulse.endInclusive)
        assertEquals(0f, after.ringAlpha, 0.0001f)
        assertEquals(Splash.finished, after.copy(head = after.head.round(), ring = Splash.HEAD))
    }

    @Test
    fun androidsSystemSplashSweepsWithinItsSecond() {
        val android = SplashTimeline.android
        assertTrue(android.drawn <= 1000f)
        val swept = Splash.pose(android.drawn, android)
        assertEquals(1f, swept.draw, 0.0001f)
        assertEquals(1f, swept.stem, 0.0001f)
    }

    private fun Offset.round() =
        Offset(
            if (abs(x - Splash.PEN_X) < 0.05f) Splash.PEN_X else x,
            if (abs(y - Splash.PEN_Y) < 0.05f) Splash.PEN_Y else y,
        )
}
