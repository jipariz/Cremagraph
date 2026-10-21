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
 * The splash: the logo playing the shot (design/splash/SPEC.md, prototype splash.html, timeline motion.py). The signal
 * tile wipes in from its left edge; the pen, the head on its stem, stands up at the edge as a playhead and sweeps through
 * the shot, the head riding the trace drawn behind it; at the plateau's end the head pulses once, a ring going out from
 * it. Everything is the logo kit's regular geometry on its 256 grid (design/logo/src/build.py): the tile drawn solid,
 * the cut drawn over it in ink, so on ink the finished frame is the mark itself.
 */

/**
 * When each part of the splash moves, in ms (motion.py's APP and ANDROID). [app] is the whole sequence; [android] the
 * system splash's compressed sweep, after which the app plays the pulse and the hand-off.
 */
data class SplashTimeline(
    val wipe: ClosedFloatingPointRange<Float>,
    /** The playhead stands up at the tile's left edge. */
    val land: ClosedFloatingPointRange<Float>,
    /** It sweeps through the shot. */
    val draw: ClosedFloatingPointRange<Float>,
    val pulse: ClosedFloatingPointRange<Float>,
    val exit: ClosedFloatingPointRange<Float>,
) {
    /** Where the mark is finished: the sweep at the pen, before the pulse. */
    val drawn: Float
        get() = draw.endInclusive

    companion object {
        val app = SplashTimeline(0f..260f, 220f..380f, 380f..1100f, 1100f..1400f, 1400f..1750f)
        /** Android 12+ sweeps in its system splash, which may animate for at most a second. */
        val android = SplashTimeline(0f..200f, 180f..300f, 300f..1000f, 1000f..1300f, 1300f..1650f)
    }
}

/** Everything about the mark at one moment, in the 256 grid. */
data class SplashPose(
    /** How much of the tile has wiped in from its left edge, 0 to 1. */
    val wipe: Float,
    /** How much of the trace is plotted behind the playhead, 0 to 1. */
    val draw: Float,
    /** How far the stem stands down from the head to its foot, 0 to 1. */
    val stem: Float,
    /** The head's centre. */
    val head: Offset,
    /** The head's size against its rest: the landing grows it from 0, the pulse swells it to 1.25. */
    val headScale: Float,
    /** The pulse's square ring around the head: its side, and how strong it still is (0 for none). */
    val ring: Float = HEAD_SIDE,
    val ringAlpha: Float = 0f,
)

private const val HEAD_SIDE = 32f

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
    private val ringEase = CubicBezierEasing(.33f, 1f, .68f, 1f)

    /** How far the pulse's ring grows past the head, and how much the head swells. */
    const val RING = 40f
    const val BUMP = 0.25f

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
        val land = landEase.transform(seg(t, tl.land))
        val pulse = seg(t, tl.pulse)
        val pulsing = pulse > 0f && pulse < 1f
        val bump = if (pulsing) sin(PI.toFloat() * min(1f, pulse * 1.6f)) * BUMP else 0f
        val ring = ringEase.transform(pulse)
        return SplashPose(
            wipe = wipeEase.transform(seg(t, tl.wipe)),
            draw = draw,
            stem = min(1f, land),
            head = pointAt(traceLength * draw + HEAD / 2),
            headScale = land * (1f + bump),
            ring = HEAD + RING * ring,
            ringAlpha = if (pulsing) 0.85f * (1f - ring) else 0f,
        )
    }

    /** The finished mark: the tile whole, the trace plotted, the playhead at rest on the pen. */
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
        // The playhead's stem stands under the head wherever it is, down to the stem's foot.
        val top = pose.head.y + Splash.HEAD / 2
        drawRect(
            ink,
            Offset(pose.head.x - Splash.STEM / 2, top),
            Size(Splash.STEM, max(0f, Splash.STEM_END - top) * pose.stem),
            alpha = alpha,
        )
    }
    val h = Splash.HEAD * pose.headScale
    if (h > 0.2f) {
        // The landing's overshoot never pokes out of the tile's left edge.
        val x = max(pose.head.x, Splash.T0 + h / 2)
        drawRect(ink, Offset(x - h / 2, pose.head.y - h / 2), Size(h, h), alpha = alpha)
    }
    if (pose.ringAlpha > 0f) {
        val r = pose.ring
        drawRect(
            ink,
            Offset(pose.head.x - r / 2, pose.head.y - r / 2),
            Size(r, r),
            alpha = alpha * pose.ringAlpha,
            style = Stroke(RING_WIDTH),
        )
    }
}

private const val RING_WIDTH = 5f
