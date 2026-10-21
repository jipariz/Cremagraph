package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import app.onlyshots.core.designsystem.CgColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * The splash: the logo recording the shot (design/splash/SPEC.md, prototype splash.html). The viewfinder's corners lock
 * in like focus, a playhead sweeps the shot curve with the dot riding its tip, the dot pulses once. Everything is the logo
 * kit's regular geometry on its 256 grid (design/logo/src/build.py), so the finished frame is the mark itself.
 */

/**
 * When each part of the splash moves, in ms. [app] is the whole sequence; [android] the system splash's compressed one.
 */
data class SplashTimeline(
    val focus: ClosedFloatingPointRange<Float>,
    val stagger: Float,
    val head: ClosedFloatingPointRange<Float>,
    val draw: ClosedFloatingPointRange<Float>,
    val pulse: ClosedFloatingPointRange<Float>,
    val exit: ClosedFloatingPointRange<Float>,
) {
    companion object {
        val app = SplashTimeline(0f..320f, 40f, 260f..420f, 380f..1100f, 1100f..1400f, 1400f..1750f)
        /** Android 12+ draws up to the pulse in its system splash, which may animate for at most a second. */
        val android = SplashTimeline(0f..280f, 30f, 220f..360f, 320f..960f, 1000f..1300f, 1300f..1650f)
    }
}

/** Everything about the mark at one moment, in the 256 grid. The dot's place comes from the curve at [progress]. */
data class SplashPose(
    /** How far each corner (top-left, top-right, bottom-right, bottom-left) still is from rest, along its diagonal. */
    val cornerOffset: List<Float>,
    val cornerAlpha: List<Float>,
    /** The playhead's half-height: it grows from y 130 to 78–182. */
    val headHalf: Float,
    /** How much of the curve is drawn, 0 to 1; the playhead and the dot ride its tip. */
    val progress: Float,
    val dotRadius: Float,
    val ringRadius: Float,
    val ringAlpha: Float,
)

object Splash {
    const val PLAYHEAD_X = 178f
    const val PLATEAU_Y = 110f
    const val START_X = 72f
    const val START_Y = 168f
    const val DOT = 18f

    /** The pose at [t] ms: the prototype's render(), less the exit, which belongs to whoever shows the mark. */
    fun pose(t: Float, timeline: SplashTimeline = SplashTimeline.app): SplashPose {
        val tl = timeline
        val offsets =
            List(4) { i ->
                val k = seg(t, tl.focus.start + i * tl.stagger, tl.focus.endInclusive + i * tl.stagger)
                (1f - outBack(k, 1.4f)) * 26f
            }
        val alphas =
            List(4) { i ->
                val from = tl.focus.start + i * tl.stagger
                outCubic(seg(t, from, from + 140f))
            }
        val head = outCubic(seg(t, tl.head.start, tl.head.endInclusive)) * 52f
        val p = inOutSine(seg(t, tl.draw.start, tl.draw.endInclusive))
        val dotIn = outBack(seg(t, tl.draw.start, tl.draw.start + 160f), 1.8f)
        val k = seg(t, tl.pulse.start, tl.pulse.endInclusive)
        val bump = sin(PI.toFloat() * min(1f, k * 1.6f)) * 0.28f
        val ring = outCubic(k)
        return SplashPose(
            cornerOffset = offsets,
            cornerAlpha = alphas,
            headHalf = head,
            progress = p,
            dotRadius = DOT * dotIn * (1f + bump),
            ringRadius = DOT + 26f * ring,
            ringAlpha = if (k > 0f && k < 1f) 0.6f * (1f - ring) else 0f,
        )
    }

    /** The finished mark: corners at rest, the curve drawn, the dot on the playhead at the plateau. */
    val finished: SplashPose = SplashPose(List(4) { 0f }, List(4) { 1f }, 52f, 1f, DOT, DOT, 0f)

    internal fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    internal fun outCubic(x: Float) = 1f - (1f - x).pow(3)

    internal fun inOutSine(x: Float) = -(cos(PI.toFloat() * x) - 1f) / 2f

    internal fun outBack(x: Float, s: Float) = 1f + (s + 1f) * (x - 1f).pow(3) + s * (x - 1f).pow(2)

    fun inOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
}

private val BracketData =
    listOf(
        "M30.0 88.0L30.0 60.0A30.0 30.0 0 0 1 60.0 30.0L88.0 30.0A10.0 10.0 0 0 1 88.0 50.0L60.0 50.0A10.0 10.0 0 0 0 " +
            "50.0 60.0L50.0 88.0A10.0 10.0 0 0 1 30.0 88.0Z",
        "M168.0 30.0L196.0 30.0A30.0 30.0 0 0 1 226.0 60.0L226.0 88.0A10.0 10.0 0 0 1 206.0 88.0L206.0 60.0A10.0 10.0 " +
            "0 0 0 196.0 50.0L168.0 50.0A10.0 10.0 0 0 1 168.0 30.0Z",
        "M226.0 168.0L226.0 196.0A30.0 30.0 0 0 1 196.0 226.0L168.0 226.0A10.0 10.0 0 0 1 168.0 206.0L196.0 206.0A10.0 " +
            "10.0 0 0 0 206.0 196.0L206.0 168.0A10.0 10.0 0 0 1 226.0 168.0Z",
        "M88.0 226.0L60.0 226.0A30.0 30.0 0 0 1 30.0 196.0L30.0 168.0A10.0 10.0 0 0 1 50.0 168.0L50.0 196.0A10.0 10.0 " +
            "0 0 0 60.0 206.0L88.0 206.0A10.0 10.0 0 0 1 88.0 226.0Z",
    )

/** Top-left, top-right, bottom-right, bottom-left: the way each corner comes in from. */
private val Diagonals = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)

/** The curve as the shot draws it, from the start of the shot to the playhead's rest. */
private fun curvePath() =
    Path().apply {
        moveTo(Splash.START_X, Splash.START_Y)
        lineTo(88f, Splash.START_Y)
        cubicTo(106f, Splash.START_Y, 112f, Splash.PLATEAU_Y, 134f, Splash.PLATEAU_Y)
        lineTo(Splash.PLAYHEAD_X, Splash.PLATEAU_Y)
    }

/** The parsed paths, built once per composition rather than per frame. */
class SplashGeometry {
    internal val brackets = BracketData.map { PathParser().parsePathString(it).toPath() }
    internal val curve = curvePath()
    internal val measure = PathMeasure().apply { setPath(curve, false) }
    internal val trimmed = Path()
}

@Composable fun rememberSplashGeometry(): SplashGeometry = remember { SplashGeometry() }

/** The mark in [pose], filling the canvas (a square, the 256 grid scaled to fit), at [alpha]. */
@Composable
fun SplashMark(pose: SplashPose, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val geometry = rememberSplashGeometry()
    Canvas(modifier) { drawSplashMark(geometry, pose, alpha) }
}

fun DrawScope.drawSplashMark(geometry: SplashGeometry, pose: SplashPose, alpha: Float = 1f) {
    val s = min(size.width, size.height) / 256f
    translate((size.width - 256f * s) / 2f, (size.height - 256f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) { drawGrid(geometry, pose, alpha) }
    }
}

private fun DrawScope.drawGrid(g: SplashGeometry, pose: SplashPose, alpha: Float) {
    val cream = CgColors.Brand.Bone
    val shot = CgColors.Brand.Signal
    g.brackets.forEachIndexed { i, path ->
        val (dx, dy) = Diagonals[i]
        val off = pose.cornerOffset[i]
        translate(dx * off, dy * off) { drawPath(path, cream, alpha = alpha * pose.cornerAlpha[i]) }
    }
    val length = g.measure.length
    val tip =
        if (pose.progress > 0f) g.measure.getPosition(length * pose.progress)
        else Offset(Splash.START_X, Splash.START_Y)
    if (pose.headHalf > 0f) {
        drawLine(
            cream,
            Offset(tip.x, 130f - pose.headHalf),
            Offset(tip.x, 130f + pose.headHalf),
            strokeWidth = 14f,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
    if (pose.progress > 0.004f) {
        g.trimmed.reset()
        g.measure.getSegment(0f, length * pose.progress, g.trimmed, true)
        drawPath(g.trimmed, shot, alpha = alpha, style = Stroke(20f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (pose.ringAlpha > 0f) {
        drawCircle(shot, pose.ringRadius, tip, alpha = alpha * pose.ringAlpha, style = Stroke(4f))
    }
    if (pose.dotRadius > 0f) drawCircle(shot, pose.dotRadius, tip, alpha = alpha)
}
