package app.onlyshots.core.model

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PumpOnsetDetectorTest {
    private val sr = 8_000

    /** Room noise, then a 120 Hz pump hum (with a harmonic) from [onsetS]. */
    private fun clip(seconds: Double, onsetS: Double?, humFrom: Double? = onsetS, humTo: Double = seconds): FloatArray {
        val rnd = Random(42)
        return FloatArray((seconds * sr).toInt()) { i ->
            val t = i.toDouble() / sr
            var v = (rnd.nextFloat() - 0.5f) * 0.01f
            if (humFrom != null && t >= humFrom && t < humTo) {
                v += (0.3 * sin(2 * PI * 120 * t) + 0.1 * sin(2 * PI * 240 * t)).toFloat()
            }
            v
        }
    }

    @Test
    fun findsPumpStart() {
        val onset = PumpOnsetDetector().detect(clip(8.0, onsetS = 3.0), sr)
        assertNotNull(onset)
        assertTrue(onset in 2_900_000L..3_150_000L, "onset at $onset")
    }

    @Test
    fun findsPumpThatRunsForMostOfTheClip() {
        // Typical shot video: one second of quiet, then the pump for the rest.
        val onset = PumpOnsetDetector().detect(clip(20.0, onsetS = 1.0), sr)
        assertNotNull(onset)
        assertTrue(onset in 900_000L..1_150_000L, "onset at $onset")
    }

    @Test
    fun clipStartingMidShotHasNoOnset() {
        assertNull(PumpOnsetDetector().detect(clip(10.0, onsetS = 0.0), sr))
    }

    @Test
    fun ignoresShortBursts() {
        // 200 ms clunk of the brew switch: in band but not sustained.
        assertNull(PumpOnsetDetector().detect(clip(6.0, onsetS = null, humFrom = 2.0, humTo = 2.2), sr))
    }

    @Test
    fun ignoresHighPitchedSound() {
        val rnd = Random(1)
        val hiss =
            FloatArray(6 * sr) { i ->
                val t = i.toDouble() / sr
                (rnd.nextFloat() - 0.5f) * 0.01f + if (t > 2) (0.3 * sin(2 * PI * 2_000 * t)).toFloat() else 0f
            }
        assertNull(PumpOnsetDetector().detect(hiss, sr))
    }

    @Test
    fun silenceHasNoOnset() {
        assertNull(PumpOnsetDetector().detect(FloatArray(4 * sr), sr))
    }
}
