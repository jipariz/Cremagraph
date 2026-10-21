package app.onlyshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.component.LocalSplashTarget
import app.onlyshots.core.designsystem.component.Splash
import app.onlyshots.core.designsystem.component.SplashTarget
import app.onlyshots.core.designsystem.component.SplashTimeline
import app.onlyshots.core.designsystem.component.drawSplashMark
import app.onlyshots.core.designsystem.component.rememberSplashGeometry
import kotlin.math.roundToInt

/** How big the splash's mark is: Android's 240 dp splash icon box at 64 %, the size every platform shows it at. */
val SplashMarkSize = 154.dp

/**
 * One cold start's splash (design/splash/SPEC.md). [held]: the finished mark is already on screen, drawn by the
 * platform's own launch screen (Android's system splash, iOS's launch screen), so only the pulse and the hand-off are
 * left, and they wait for [release]. [markBounds]: where that mark is, in the window, when the platform says.
 */
class SplashState(val held: Boolean, val timeline: SplashTimeline = SplashTimeline.app) {
    var released by mutableStateOf(!held)
        private set

    var markBounds by mutableStateOf<Rect?>(null)

    companion object {
        /** After Android's system splash, which has drawn the shot on its own shorter timeline. */
        fun afterSystemSplash() = SplashState(held = true, timeline = SplashTimeline.android)
    }

    /** The platform's own splash has gone: the app's carries on from it. */
    fun release(bounds: Rect? = null) {
        if (bounds != null) markBounds = bounds
        released = true
    }
}

/** A splash plays once per process: back from the background, or a recreated activity, shows the app at once. */
object SplashOnce {
    var played = false
}

/**
 * The app under its splash. The app is composed from the first frame, underneath; over it the mark records the shot
 * (unless [splash] is [SplashState.held]), always to the end of the draw, holds there until the platform's splash has
 * gone ([SplashState.release]), pulses once, then flies into the first screen's own mark ([SplashTarget]) or, without
 * one, eases back and fades.
 */
@Composable
fun SplashHost(splash: SplashState?, content: @Composable () -> Unit) {
    val show = remember { splash != null && !SplashOnce.played }
    if (!show || splash == null) {
        content()
        return
    }
    val target = remember { SplashTarget().apply { hidden = true } }
    val reduced = rememberReducedMotion()
    val tl = splash.timeline
    var t by remember { mutableFloatStateOf(if (splash.held) tl.draw.endInclusive else 0f) }
    // Reduced motion: no clock, only these two fades.
    var fadeIn by remember { mutableFloatStateOf(if (splash.held) 1f else 0f) }
    var fadeOut by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        suspend fun advance(until: Float, fromMs: Long? = null) {
            var last = fromMs ?: withFrameMillis { it }
            while (t < until) {
                withFrameMillis { now ->
                    t = (t + (now - last)).coerceAtMost(until)
                    last = now
                }
            }
        }
        suspend fun waitUntil(condition: () -> Boolean) {
            while (!condition()) withFrameMillis {}
        }
        if (reduced) {
            // Already on screen when the platform's own splash showed it.
            if (!splash.held) animate(200f) { fadeIn = it }
            waitUntil { splash.released }
            animate(300f) { fadeOut = it }
        } else {
            // The draw always plays to its end; then the finished mark waits, if it has to, for the platform's splash.
            advance(tl.draw.endInclusive)
            waitUntil { splash.released }
            advance(tl.pulse.start)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            advance(tl.exit.endInclusive)
        }
        target.hidden = false
        SplashOnce.played = true
        done = true
    }

    CompositionLocalProvider(LocalSplashTarget provides target) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (!done) {
                if (reduced) ReducedOverlay(fadeIn, fadeOut, splash.markBounds)
                else Overlay(t, tl, splash.markBounds, target.bounds)
            }
        }
    }
}

/** Runs 0 → 1 over [ms], frame by frame. */
private suspend fun animate(ms: Float, set: (Float) -> Unit) {
    val start = withFrameMillis { it }
    var k = 0f
    while (k < 1f) {
        withFrameMillis { now -> k = ((now - start) / ms).coerceIn(0f, 1f) }
        set(k)
    }
}

@Composable
private fun Overlay(t: Float, tl: SplashTimeline, held: Rect?, landing: Rect?) {
    val geometry = rememberSplashGeometry()
    val logo = CgIcons.OnlyShots
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val markPx = with(density) { SplashMarkSize.toPx() }
        val start =
            held
                ?: Rect(
                    Offset((constraints.maxWidth - markPx) / 2f, (constraints.maxHeight - markPx) / 2f),
                    Size(markPx, markPx),
                )
        val e = Splash.inOutCubic(((t - tl.exit.start) / (tl.exit.endInclusive - tl.exit.start)).coerceIn(0f, 1f))
        // The screen comes up from under the splash as the mark sets off.
        val screen = ((t - tl.exit.start - 120f) / (tl.exit.endInclusive - tl.exit.start - 120f)).coerceIn(0f, 1f)
        val rect: Rect
        val markAlpha: Float
        // The app's own mark, drawn in the last stretch over the splash's so the hand-over is seamless: at 32 dp the
        // logo takes its small cut, a little heavier than the splash's regular weight.
        val logoAlpha: Float
        if (landing != null) {
            rect = lerp(start, landing, e)
            logoAlpha = ((e - 0.7f) / 0.3f).coerceIn(0f, 1f)
            markAlpha = 1f - logoAlpha
        } else {
            val s = 1f - 0.08f * e
            rect = Rect(start.center, start.width * s / 2f)
            markAlpha = 1f - e
            logoAlpha = 0f
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = 1f - if (landing != null) screen else e }
                .background(CgColors.Surface.Base)
        )
        val pose = Splash.pose(t, tl)
        val sizeDp = with(density) { rect.width.toDp() }
        val at = IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
        Canvas(Modifier.offset { at }.size(sizeDp)) { drawSplashMark(geometry, pose, markAlpha) }
        if (logoAlpha > 0f) {
            Image(logo, null, Modifier.offset { at }.size(sizeDp).graphicsLayer { alpha = logoAlpha })
        }
    }
}

/** Reduced motion: the finished mark fades in, holds, and cross-fades to the app. Nothing moves. */
@Composable
private fun ReducedOverlay(fadeIn: Float, fadeOut: Float, held: Rect?) {
    val geometry = rememberSplashGeometry()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - fadeOut }.background(CgColors.Surface.Base)
    ) {
        val markPx = with(density) { SplashMarkSize.toPx() }
        val rect =
            held
                ?: Rect(
                    Offset((constraints.maxWidth - markPx) / 2f, (constraints.maxHeight - markPx) / 2f),
                    Size(markPx, markPx),
                )
        Canvas(
            Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() })
        ) {
            drawSplashMark(geometry, Splash.finished, fadeIn)
        }
    }
}

/** Whether the platform asks for less motion: the animator scale at 0 or "remove animations", Reduce Motion, … */
@Composable internal expect fun rememberReducedMotion(): Boolean
