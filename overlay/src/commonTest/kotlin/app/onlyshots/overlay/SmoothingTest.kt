package app.onlyshots.overlay

import app.onlyshots.overlay.scene.smoothRun
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmoothingTest {
    private fun ys(run: FloatArray) = FloatArray(run.size / 2) { run[2 * it + 1] }

    private fun xs(run: FloatArray) = FloatArray(run.size / 2) { run[2 * it] }

    @Test
    fun twoPointsStayAStraightLine() {
        val run = floatArrayOf(0f, 10f, 30f, 40f)
        assertContentEquals(run, smoothRun(run, top = 0f, bottom = 100f))
    }

    @Test
    fun aPlateauStaysFlat() {
        // Slopes either side of the middle point are 0 and 0: every tangent is flat, so every point is y = 50.
        val out = smoothRun(floatArrayOf(0f, 50f, 30f, 50f, 60f, 50f), top = 0f, bottom = 100f)
        assertTrue(ys(out).all { it == 50f })
    }

    @Test
    fun aRiseThatLevelsOffNeverOvershoots() {
        // 0 → 40 over 30 px, then flat. A Catmull-Rom spline swings above 40 before the plateau; the monotone cubic
        // gives the corner a flat tangent (the slopes either side are 4/3 and 0, their product 0) and stays at or under
        // 40.
        val out = smoothRun(floatArrayOf(0f, 0f, 30f, 40f, 60f, 40f, 90f, 40f), top = -1000f, bottom = 1000f)
        assertTrue(ys(out).all { it <= 40f + 1e-4f }, "overshoot: ${ys(out).max()}")
        assertTrue(ys(out).all { it >= 0f - 1e-4f })
    }

    @Test
    fun aMonotoneRunStaysMonotone() {
        val out = smoothRun(floatArrayOf(0f, 0f, 20f, 5f, 40f, 30f, 60f, 32f, 80f, 70f), top = -1000f, bottom = 1000f)
        val y = ys(out)
        for (i in 1 until y.size) assertTrue(y[i] >= y[i - 1] - 1e-4f, "dips at $i: ${y[i - 1]} → ${y[i]}")
    }

    @Test
    fun theSamplesThemselvesAreKeptAndWideSpansGetPointsBetween() {
        // Spans of 30 px with 3 px per split, capped at 6: each span becomes 6 parts, 1 + 6 + 6 points.
        val out = smoothRun(floatArrayOf(0f, 0f, 30f, 20f, 60f, 30f), top = 0f, bottom = 100f)
        assertEquals(13, out.size / 2)
        assertEquals(30f, xs(out)[6])
        assertEquals(20f, ys(out)[6], 1e-4f)
        assertEquals(60f, xs(out).last())
    }

    @Test
    fun denseSamplesAreNotSplit() {
        val dense = FloatArray(20) { if (it % 2 == 0) it * 1f else (it * it).toFloat() }
        assertEquals(dense.size, smoothRun(dense, top = -1e6f, bottom = 1e6f).size)
    }

    @Test
    fun nothingIsDrawnOutsideThePlot() {
        val out = smoothRun(floatArrayOf(0f, 95f, 30f, 100f, 60f, 20f), top = 10f, bottom = 100f)
        assertTrue(ys(out).all { it in 10f..100f })
    }
}
