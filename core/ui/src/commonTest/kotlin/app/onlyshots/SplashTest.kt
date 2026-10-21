package app.onlyshots

import app.onlyshots.core.designsystem.component.Splash
import app.onlyshots.core.designsystem.component.SplashTimeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The splash's timeline, as design/splash/SPEC.md and its prototype have it. */
class SplashTest {
    private val app = SplashTimeline.app

    @Test
    fun theCornersComeInFromTheirDiagonalAndLock() {
        val start = Splash.pose(0f)
        start.cornerOffset.forEach { assertEquals(26f, it, 0.001f) }
        start.cornerAlpha.forEach { assertEquals(0f, it, 0.001f) }
        // Each corner 40 ms after the last, all at rest when the last has had its 320 ms.
        val locked = Splash.pose(app.focus.endInclusive + 3 * app.stagger)
        locked.cornerOffset.forEach { assertEquals(0f, it, 0.001f) }
        locked.cornerAlpha.forEach { assertEquals(1f, it, 0.001f) }
        // Out-back: the corner overshoots its rest on the way in, like focus locking.
        assertTrue((0..320).any { Splash.pose(it.toFloat()).cornerOffset[0] < 0f })
    }

    @Test
    fun theShotIsDrawnBehindThePlayheadAndTheDotPulsesOnce() {
        assertEquals(0f, Splash.pose(app.draw.start).progress, 0.0001f)
        val drawn = Splash.pose(app.draw.endInclusive)
        assertEquals(1f, drawn.progress, 0.0001f)
        assertEquals(52f, drawn.headHalf, 0.001f)
        assertEquals(Splash.DOT, drawn.dotRadius, 0.001f)
        // The pulse swells the dot and lets a ring go, and is over by its end: the finished mark again.
        val swell = (1100..1400 step 10).maxOf { Splash.pose(it.toFloat()).dotRadius }
        assertEquals(Splash.DOT * 1.28f, swell, 0.05f)
        assertTrue(Splash.pose(1200f).ringAlpha > 0f)
        val after = Splash.pose(app.pulse.endInclusive)
        assertEquals(Splash.DOT, after.dotRadius, 0.001f)
        assertEquals(0f, after.ringAlpha, 0.0001f)
        assertEquals(Splash.finished, after.copy(ringRadius = Splash.DOT))
    }

    @Test
    fun androidsSystemSplashDrawsWithinItsSecond() {
        val android = SplashTimeline.android
        assertTrue(android.draw.endInclusive <= 1000f)
        assertEquals(1f, Splash.pose(android.draw.endInclusive, android).progress, 0.0001f)
    }
}
