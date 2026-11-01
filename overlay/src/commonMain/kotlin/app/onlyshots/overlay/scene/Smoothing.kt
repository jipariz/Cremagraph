package app.onlyshots.overlay.scene

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * A run of points smoothed with a monotone cubic (Fritsch–Carlson), tessellated back into a polyline so every painter
 * draws it with the straight segments it already knows.
 *
 * Monotone, unlike a Catmull-Rom spline (what most chart libraries use, HDCharts among them): between two samples the
 * curve never goes above or below both of them, so a pressure plateau stays flat and a flow that only rises never dips
 * on the way. [top] and [bottom] clamp y on top of that, so nothing is drawn outside the plot.
 *
 * Spans narrower than [minSpanPx] stay straight, and wider ones get one extra point per [minSpanPx] up to [maxSplits]:
 * dense 10 Hz data barely grows, a sparse shot gets visibly round.
 *
 * The readings are low-passed first, so the curve runs between them rather than through each one. Machines report in
 * steps (Gaggiuino sends every reading as an integer tenth), and a monotone curve through two equal readings has to be
 * flat between them: faithful drawing of quantized data is a staircase. The labels still show the readings themselves.
 *
 * @param run x0, y0, x1, y1, … in pixels, x increasing.
 */
fun smoothRun(run: FloatArray, top: Float, bottom: Float, minSpanPx: Float = 3f, maxSplits: Int = 6): FloatArray {
    val n = run.size / 2
    if (n < 3) return run
    val y = soften(run, n)
    val slope = FloatArray(n - 1)
    for (k in 0 until n - 1) {
        val dx = run[2 * k + 2] - run[2 * k]
        slope[k] = if (dx > 0f) (y[k + 1] - y[k]) / dx else 0f
    }
    val m = FloatArray(n)
    m[0] = slope[0]
    m[n - 1] = slope[n - 2]
    for (k in 1 until n - 1) {
        // A peak or a valley gets a flat tangent; otherwise the average of the slopes either side.
        m[k] = if (slope[k - 1] * slope[k] <= 0f) 0f else (slope[k - 1] + slope[k]) / 2f
    }
    for (k in 0 until n - 1) {
        if (slope[k] == 0f) {
            m[k] = 0f
            m[k + 1] = 0f
            continue
        }
        val a = m[k] / slope[k]
        val b = m[k + 1] / slope[k]
        val r = a * a + b * b
        // Fritsch–Carlson: inside the circle of radius 3 the cubic stays monotone on this span.
        if (r > 9f) {
            val t = 3f / sqrt(r)
            m[k] = t * a * slope[k]
            m[k + 1] = t * b * slope[k]
        }
    }

    var count = 1
    for (k in 0 until n - 1) count += splits(run[2 * k + 2] - run[2 * k], minSpanPx, maxSplits)
    val out = FloatArray(count * 2)
    out[0] = run[0]
    out[1] = y[0].coerceIn(top, bottom)
    var o = 2
    for (k in 0 until n - 1) {
        val x0 = run[2 * k]
        val y0 = y[k]
        val x1 = run[2 * k + 2]
        val y1 = y[k + 1]
        val h = x1 - x0
        val parts = splits(h, minSpanPx, maxSplits)
        for (p in 1..parts) {
            val s = p.toFloat() / parts
            val s2 = s * s
            val s3 = s2 * s
            // Cubic Hermite basis on [0, 1], with the tangents scaled by the span width.
            val y =
                (2 * s3 - 3 * s2 + 1) * y0 +
                    (s3 - 2 * s2 + s) * h * m[k] +
                    (-2 * s3 + 3 * s2) * y1 +
                    (s3 - s2) * h * m[k + 1]
            out[o++] = x0 + h * s
            out[o++] = y.coerceIn(top, bottom)
        }
    }
    return out
}

/**
 * The run's y values with each inner one pulled a quarter of the way towards its neighbours (a 1, 2, 1 kernel). One
 * pass is enough to turn a pair of equal readings into a rise, while a plateau of three or more keeps its flat middle
 * and the two ends stay exactly where they were.
 *
 * ponytail: the kernel counts samples, not time, which is what makes it carry its weight at every sample rate - dense
 * 10 Hz data moves by a fraction of a pixel, a sparse shot loses its steps. Readings spaced unevenly are averaged as if
 * they were not; no shot seen so far varies enough for that to show.
 */
private fun soften(run: FloatArray, n: Int): FloatArray {
    val y = FloatArray(n) { run[2 * it + 1] }
    val out = y.copyOf()
    for (k in 1 until n - 1) out[k] = (y[k - 1] + 2f * y[k] + y[k + 1]) / 4f
    return out
}

private fun splits(spanPx: Float, minSpanPx: Float, maxSplits: Int): Int =
    if (spanPx <= minSpanPx) 1 else ceil(spanPx / minSpanPx).toInt().coerceIn(1, maxSplits)
