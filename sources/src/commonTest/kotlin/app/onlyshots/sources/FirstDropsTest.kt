package app.onlyshots.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirstDropsTest {
    private val d = FirstDropsDetector()
    private var t = 0L

    /** Readings every 100 ms. */
    private fun feed(vararg grams: Float): List<FirstDropsDetector.Result> = grams.map { g ->
        d.onReading(t, g).also { t += 100_000L }
    }

    private fun hold(g: Float, readings: Int = 8) = feed(*FloatArray(readings) { g })

    private fun List<FirstDropsDetector.Result>.start() = firstNotNullOfOrNull { it.started }

    @Test
    fun aCupAlreadyOnTheScaleIsTheZeroNotAShot() {
        assertTrue(hold(182.4f).none { it.started != null })
        val startAt = t + 100_000L
        val drops = feed(182.6f, 182.9f, 183.3f)
        // 0.2 g is not drops; two readings over 0.3 g are, from the first of them.
        assertEquals(BrewEvent.Started(startAt), drops.start())
        assertEquals(0.9f, drops.last().weight, 0.01f)
    }

    @Test
    fun aScaleThatOnlyReportsChangesStillStarts() {
        // Tared and quiet: one reading of 0, then nothing for seconds, then the drops.
        feed(0f)
        t += 4_000_000L
        assertNotNull(feed(0.4f, 0.8f, 1.3f).start())
    }

    @Test
    fun oneNoisyReadingIsNotDrops() {
        hold(0f)
        assertNull(feed(0.5f, 0f, 0.1f, 0f).start())
    }

    @Test
    fun aWanderingDisplayStillSettles() {
        assertNull(feed(0.1f, -0.1f, 0.2f, 0f, -0.1f, 0.1f, 0.2f, 0f).start())
        assertNotNull(feed(0.6f, 1f).start())
    }

    @Test
    fun puttingACupDownWhileArmedIsNotDrops() {
        hold(0f)
        val placing = feed(60f, 180f, 182f, 181.8f, 182f) + hold(182f)
        assertTrue(placing.none { it.started != null })
        assertEquals(0f, placing.last().weight, 0.1f)
        assertNotNull(feed(182.4f, 182.7f).start())
    }

    @Test
    fun liftingTheCupReArmsForTheNextShot() {
        hold(182f)
        assertNotNull(feed(182.5f, 182.9f).start())
        feed(190f, 200f, 220f) // the shot
        hold(0f) // cup off
        hold(150f) // a fresh cup on
        val second = feed(150.4f, 150.8f)
        assertNotNull(second.start())
        assertEquals(0.4f, second.first().weight, 0.01f)
    }

    @Test
    fun theScalesTareButtonReArmsToo() {
        hold(0f)
        assertNotNull(feed(0.5f, 1f).start())
        feed(20f, 36f)
        hold(0f) // tare: back to 0
        assertNotNull(feed(0.4f, 0.9f).start())
    }

    @Test
    fun nothingStartsBeforeTheWeightHasSettled() {
        assertNull(feed(0f, 0.5f, 1f, 1.5f, 2f).start())
    }

    @Test
    fun aFastPourDuringTheShotStaysInTheShot() {
        // From the phone's log: a gentle start, then 5–15 g per reading.
        hold(0f)
        assertNotNull(feed(0.5f, 4.9f).start())
        val pour = feed(14.3f, 22.7f, 26.9f, 33.4f, 41f, 46.7f, 51.3f)
        assertTrue(d.started && d.brewing)
        assertEquals(51.3f, pour.last().weight, 0.01f)
    }

    @Test
    fun aPourThatStartsFastStartsTheShotAtTheJump() {
        hold(0f)
        val jumpAt = t
        val pour = feed(8f, 16f, 25f, 33f, 41f, 50f, 58f, 66f, 75f, 83f, 91f, 99f)
        assertEquals(BrewEvent.Started(jumpAt), pour.start())
        assertTrue(pour.all { it.weight > 0f }, "measured from before the jump")
    }

    @Test
    fun aCupThatLandsIsStillNotAPour() {
        hold(0f)
        val placing = feed(40f, 120f, 180f, 182f, 182.1f, 182f, 182f, 182f, 182f, 182f, 182f, 182f, 182f)
        assertNull(placing.start())
        assertFalse(d.brewing)
    }

    @Test
    fun liftingTheCupEndsTheShotAtThatReading() {
        hold(182f)
        assertNotNull(feed(182.5f, 182.9f).start())
        // Still dripping when the cup comes off: the reading that drops is the end, and no other is.
        val shot = feed(200f, 220f, 236.1f, 236.2f)
        assertTrue(shot.none { it.ended != null })
        val offAt = t
        val off = feed(-118f, -118f)
        assertEquals(BrewEvent.Ended(offAt), off.first().ended)
        assertNull(off.last().ended)
        assertFalse(d.brewing)
    }
}
