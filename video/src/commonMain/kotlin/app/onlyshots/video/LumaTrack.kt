package app.onlyshots.video

import app.onlyshots.core.model.NormRect
import kotlin.math.pow
import okio.Path

/**
 * How bright the video is behind the graph, over time: the input of "adapt to video", the same in the editor's preview
 * and in the export. Values are relative luminance 0..1 (linear light, as the contrast maths wants), smoothed over half
 * a second so the overlay does not flicker between its light and dark sets.
 */
class LumaTrack(private val startUs: Long, private val stepUs: Long, private val values: FloatArray) {
    /** The luminance at [videoUs], between the two nearest samples; the ends hold. */
    fun at(videoUs: Long): Float? {
        if (values.isEmpty()) return null
        val pos = (videoUs - startUs).toFloat() / stepUs
        val i = pos.toInt().coerceIn(0, values.size - 1)
        val j = (i + 1).coerceAtMost(values.size - 1)
        val f = (pos - i).coerceIn(0f, 1f)
        return values[i] + (values[j] - values[i]) * f
    }

    companion object {
        const val STEP_US = 250_000L

        /** Time constant of the smoothing, as the spec asks (0.5 s). */
        private const val SMOOTH_US = 500_000L

        /**
         * Measures [file] from [fromUs] to [toUs] inside [rect] (a 4 × 3 grid of points), [STEP_US] apart. [rect] is in
         * the output frame; [toSource] maps it into the picture as decoded (the crop and reframe, see
         * `VideoItem.sourceMapping`). Null when no frame could be read.
         */
        suspend fun measure(
            sampler: FrameSampler,
            file: Path,
            rect: NormRect,
            fromUs: Long,
            toUs: Long,
            toSource: (Float, Float) -> Pair<Float, Float> = { x, y -> x to y },
        ): LumaTrack? {
            val points = buildList {
                for (gy in 0 until 3) for (gx in 0 until 4) {
                    add(toSource(rect.x + rect.w * (gx + 0.5f) / 4f, rect.y + rect.h * (gy + 0.5f) / 3f))
                }
            }
            val raw = ArrayList<Float>()
            var t = fromUs
            while (t <= toUs) {
                val lumas = sampler.lumaAt(file, t, points)
                raw += lumas?.let { l -> l.map(::linear).average().toFloat() } ?: raw.lastOrNull() ?: return null
                t += STEP_US
            }
            if (raw.isEmpty()) return null
            return LumaTrack(fromUs, STEP_US, smooth(raw.toFloatArray()))
        }

        /** The samplers give gamma-encoded luma; contrast is worked out in linear light. */
        private fun linear(luma: Float): Float = luma.coerceIn(0f, 1f).pow(2.2f)

        /** A first-order low-pass forwards then backwards, so the smoothing neither lags nor leads. */
        internal fun smooth(v: FloatArray): FloatArray {
            if (v.size < 2) return v
            val a = (STEP_US.toFloat() / (SMOOTH_US + STEP_US)).coerceIn(0f, 1f)
            val out = v.copyOf()
            for (i in 1 until out.size) out[i] = out[i - 1] + a * (out[i] - out[i - 1])
            for (i in out.size - 2 downTo 0) out[i] = out[i + 1] + a * (out[i] - out[i + 1])
            return out
        }
    }
}
