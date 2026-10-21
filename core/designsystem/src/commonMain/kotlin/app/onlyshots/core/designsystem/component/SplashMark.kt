package app.onlyshots.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.onlyshots.core.designsystem.CgColors
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * The splash: the logo plotting the shot (design/splash/SPEC.md, prototype splash.html, timeline motion.py). The signal
 * tile wipes in from its left edge, the pen head lands on it and plots the shot's trace across it, the stem drops, the
 * head ticks to mark "now". Everything is the logo kit's regular geometry on its 256 grid (design/logo/src/build.py):
 * the tile drawn solid, the cut drawn over it in ink, so on ink the finished frame is the mark itself.
 */

/**
 * When each part of the splash moves, in ms (motion.py's APP and ANDROID). [app] is the whole sequence; [android] the
 * system splash's compressed draw, after which the app plays the tick and the hand-off.
 */
data class SplashTimeline(
    val wipe: ClosedFloatingPointRange<Float>,
    val land: ClosedFloatingPointRange<Float>,
    val draw: ClosedFloatingPointRange<Float>,
    val stem: ClosedFloatingPointRange<Float>,
    val tick: ClosedFloatingPointRange<Float>,
    val exit: ClosedFloatingPointRange<Float>,
) {
    /** Where the mark is finished: the draw and the stem done, before the tick. */
    val drawn: Float
        get() = stem.endInclusive

    companion object {
        val app = SplashTimeline(0f..260f, 220f..340f, 300f..1000f, 1000f..1120f, 1120f..1400f, 1400f..1750f)
        /** Android 12+ draws up to the stem in its system splash, which may animate for at most a second. */
        val android = SplashTimeline(0f..200f, 180f..260f, 240f..880f, 880f..1000f, 1000f..1280f, 1280f..1630f)
    }
}

/** Everything about the mark at one moment, in the 256 grid. */
data class SplashPose(
    /** How much of the tile has wiped in from its left edge, 0 to 1. */
    val wipe: Float,
    /** How much of the trace is plotted, 0 to 1. */
    val draw: Float,
    /** How far the stem has dropped from the head, 0 to 1. */
    val stem: Float,
    /** The head's centre. */
    val head: Offset,
    /** The head's size against its rest: the landing grows it from 0, the tick swells it to 1.2. */
    val headScale: Float,
)

object Splash {
    const val T0 = 32f
    const val T1 = 224f
    const val PEN_X = 172f
    const val PEN_Y = 100f
    const val FLAT_Y = 166f
    const val RISE_FROM = 80f
    const val RISE_TO = 140f
    const val STEM_END = 196f
    const val TRACE = 20f
    const val HEAD = 32f
    const val STEM = 12f

    /** The trace stops at the head's left edge; the head's centre runs half its side ahead of the trace's tip. */
    const val TRACE_END = PEN_X - HEAD / 2

    private val wipeEase = CubicBezierEasing(.33f, 1f, .68f, 1f)
    private val landEase = CubicBezierEasing(.34f, 1.6f, .64f, 1f)
    private val drawEase = CubicBezierEasing(.37f, 0f, .63f, 1f)
    private val stemEase = CubicBezierEasing(.33f, 1f, .68f, 1f)

    /**
     * The centreline as points (motion.py's PATH: build.centreline with 400 steps, then on to the pen), plain numbers
     * so a pose needs no graphics: the trace up to the head's left edge, then the last half head the head rides ahead.
     */
    private val points: List<Offset> = buildList {
        val mid = (RISE_FROM + RISE_TO) / 2f
        add(Offset(T0, FLAT_Y))
        for (i in 0..400) {
            val t = i / 400f
            val u = 1f - t
            val x = u * u * u * RISE_FROM + 3 * u * u * t * mid + 3 * u * t * t * mid + t * t * t * RISE_TO
            val y = u * u * u * FLAT_Y + 3 * u * u * t * FLAT_Y + 3 * u * t * t * PEN_Y + t * t * t * PEN_Y
            add(Offset(x, y))
        }
        add(Offset(TRACE_END, PEN_Y))
        add(Offset(PEN_X, PEN_Y))
    }
    private val lengths: FloatArray =
        FloatArray(points.size).also { l ->
            for (i in 1 until points.size) l[i] = l[i - 1] + (points[i] - points[i - 1]).getDistance()
        }
    /** The whole line runs on to the pen; the trace is all but the last half head. */
    private val traceLength = lengths.last() - HEAD / 2

    private fun pointAt(distance: Float): Offset {
        val d = distance.coerceIn(0f, lengths.last())
        for (i in 1 until points.size) {
            if (lengths[i] >= d) {
                val span = (lengths[i] - lengths[i - 1]).coerceAtLeast(1e-6f)
                return lerp(points[i - 1], points[i], (d - lengths[i - 1]) / span)
            }
        }
        return points.last()
    }

    /** The pose at [t] ms: the prototype's state(), less the exit, which belongs to whoever shows the mark. */
    fun pose(t: Float, timeline: SplashTimeline = SplashTimeline.app): SplashPose {
        val tl = timeline
        val draw = drawEase.transform(seg(t, tl.draw))
        val tick = seg(t, tl.tick)
        val bump = if (tick > 0f && tick < 1f) sin(PI.toFloat() * min(1f, tick / 0.6f)) * 0.2f else 0f
        return SplashPose(
            wipe = wipeEase.transform(seg(t, tl.wipe)),
            draw = draw,
            stem = stemEase.transform(seg(t, tl.stem)),
            head = pointAt(traceLength * draw + HEAD / 2),
            headScale = landEase.transform(seg(t, tl.land)) * (1f + bump),
        )
    }

    /** The finished mark: the tile whole, the trace plotted, the stem down, the head at rest on the pen. */
    val finished: SplashPose = SplashPose(1f, 1f, 1f, Offset(PEN_X, PEN_Y), 1f)

    internal fun seg(t: Float, r: ClosedFloatingPointRange<Float>) =
        ((t - r.start) / (r.endInclusive - r.start)).coerceIn(0f, 1f)

    fun inOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
}

/**
 * The trace's centreline as the logo draws it: the preinfusion flat from the tile's left edge, the S-curve rise from x
 * 80 to 140, the plateau to the head's left edge. For drawing; [Splash.pose] walks the same line as points.
 */
private fun tracePath() =
    Path().apply {
        val mid = (Splash.RISE_FROM + Splash.RISE_TO) / 2f
        moveTo(Splash.T0, Splash.FLAT_Y)
        lineTo(Splash.RISE_FROM, Splash.FLAT_Y)
        cubicTo(mid, Splash.FLAT_Y, mid, Splash.PEN_Y, Splash.RISE_TO, Splash.PEN_Y)
        lineTo(Splash.TRACE_END, Splash.PEN_Y)
    }

/** The parsed paths, built once per composition rather than per frame. */
class SplashGeometry {
    internal val trace = tracePath()
    internal val measure = PathMeasure().apply { setPath(trace, false) }
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
    val tile = CgColors.Brand.Signal
    val ink = CgColors.Surface.Base
    val side = Splash.T1 - Splash.T0
    if (pose.wipe > 0f) drawRect(tile, Offset(Splash.T0, Splash.T0), Size(side * pose.wipe, side), alpha = alpha)
    val length = g.measure.length
    if (pose.draw > 0.002f) {
        g.trimmed.reset()
        g.measure.getSegment(0f, length * pose.draw, g.trimmed, true)
        drawPath(
            g.trimmed,
            ink,
            alpha = alpha,
            style = Stroke(Splash.TRACE, cap = StrokeCap.Butt, join = StrokeJoin.Round),
        )
    }
    if (pose.stem > 0f) {
        val top = Splash.PEN_Y + Splash.HEAD / 2
        drawRect(
            ink,
            Offset(Splash.PEN_X - Splash.STEM / 2, top),
            Size(Splash.STEM, (Splash.STEM_END - top) * pose.stem),
            alpha = alpha,
        )
    }
    val h = Splash.HEAD * pose.headScale
    if (h > 0.2f) {
        // The landing's overshoot never pokes out of the tile's left edge.
        val x = max(pose.head.x, Splash.T0 + h / 2)
        drawRect(ink, Offset(x - h / 2, pose.head.y - h / 2), Size(h, h), alpha = alpha)
    }
}
