package app.onlyshots.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds where the pump starts in an imported video's audio: the first point where energy in the 50–300 Hz band rises
 * well above the quiet level and stays there for more than 300 ms. Places the shot-start flag; the user can still move
 * it.
 */
class PumpOnsetDetector(
    private val lowHz: Float = 50f,
    private val highHz: Float = 300f,
    private val frameMs: Int = 20,
    private val sustainMs: Int = 300,
    /** How far above the quiet baseline the band energy must rise, as a power ratio (≈ +12 dB). */
    private val riseRatio: Float = 16f,
) {
    /** Onset in µs from the start of [pcm], or null if nothing pump-like was found. */
    fun detect(pcm: FloatArray, sampleRate: Int): Long? {
        val band = bandPass(pcm, sampleRate)
        val frame = sampleRate * frameMs / 1000
        if (frame <= 0 || band.size < frame * 2) return null
        return onsetOf(
            FloatArray(band.size / frame) { f ->
                var e = 0.0
                for (i in f * frame until (f + 1) * frame) e += band[i] * band[i]
                (e / frame).toFloat()
            }
        )
    }

    /**
     * The decision on its own, taking band energy per [frameMs] frame. The web editor band-passes in the browser, where
     * the samples already are, and calls this so the rule that places the flag is the phones' rule.
     */
    fun onsetOf(energies: FloatArray): Long? {
        // Quiet level: the quietest stretch so far (3-frame average), so a pump that runs for
        // most of the clip still stands out against the few seconds before it.
        val needed = (sustainMs + frameMs - 1) / frameMs + 1
        var quiet = Float.MAX_VALUE
        var runStart = -1
        for (i in energies.indices) {
            val loud = quiet != Float.MAX_VALUE && energies[i] > maxOf(quiet, NOISE_FLOOR) * riseRatio
            if (loud) {
                if (runStart < 0) runStart = i
                if (i - runStart + 1 >= needed) return runStart.toLong() * frameMs * 1000
            } else {
                runStart = -1
                if (i >= 2) quiet = minOf(quiet, (energies[i] + energies[i - 1] + energies[i - 2]) / 3f)
            }
        }
        return null
    }

    private companion object {
        /** Band energy of digital silence plus dither; keeps the threshold above zero. */
        const val NOISE_FLOOR = 1e-8f
    }

    /**
     * Two second-order high-pass stages at [lowHz], then two low-pass stages at [highHz]. The quiet level is measured
     * from near silence, so loud sound outside the band (milk jug, grinder whine) must be attenuated hard enough not to
     * look like the pump.
     */
    private fun bandPass(x: FloatArray, sampleRate: Int): FloatArray {
        val hp = Biquad.highPass(lowHz, sampleRate)
        val lp = Biquad.lowPass(highHz, sampleRate)
        return lp.process(lp.process(hp.process(hp.process(x))))
    }

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        fun process(x: FloatArray): FloatArray {
            val y = FloatArray(x.size)
            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            for (i in x.indices) {
                val xi = x[i].toDouble()
                val yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = xi
                y2 = y1
                y1 = yi
                y[i] = yi.toFloat()
            }
            return y
        }

        companion object {
            private val Q = 1.0 / sqrt(2.0)

            fun lowPass(hz: Float, sr: Int): Biquad {
                val w = 2 * PI * hz / sr
                val alpha = sin(w) / (2 * Q)
                val c = cos(w)
                val a0 = 1 + alpha
                return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
            }

            fun highPass(hz: Float, sr: Int): Biquad {
                val w = 2 * PI * hz / sr
                val alpha = sin(w) / (2 * Q)
                val c = cos(w)
                val a0 = 1 + alpha
                return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
            }
        }
    }
}
