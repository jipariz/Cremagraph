package app.onlyshots.core.designsystem

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/**
 * How things move, the Instrument way: like hardware. Things travel fast and stop dead (one curve, no springs, no
 * overshoot, no scale); a value that changes ticks over like a readout ([tick]) or just changes ([cut]); panels, sheets
 * and banners slide, they don't fade; indicators blink in steps ([rememberBlink]), they don't breathe. Fades are kept
 * for overlays only (a scrim, the graph over the camera). Every animated value starts at its target, so screenshots
 * show the settled state.
 */
object CgMotion {
    /** Colour, a knob, a key: small things that change in place. */
    const val SHORT = 90
    /** Something arriving or leaving: a row, a banner, a section. */
    const val MEDIUM = 160
    /** Sheets and screens. Nothing takes longer, but a progress run. */
    const val LONG = 240

    /** The one curve: quick off the mark, a short firm stop, no long tail. */
    val Mechanical = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Gauges and sweeps: an even rate. */
    val Linear = LinearEasing

    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT, easing = Mechanical)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Mechanical)

    fun <T> long(): FiniteAnimationSpec<T> = tween(LONG, easing = Mechanical)

    /** Things that settle on a target: a knob, a snapped edge, an underline. It stops exactly there. */
    fun <T> settle(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Mechanical)

    /** A layout moving into a new shape (a format picked, a panel moving): it never runs past. */
    fun <T> layout(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Mechanical)

    /** The one thing the hand just did (the record key, a dropped graph): quick, and no give. */
    fun <T> springy(): FiniteAnimationSpec<T> = tween(SHORT, easing = Mechanical)

    /** A gauge moving to a new reading: an even rate, like a needle. */
    fun <T> gauge(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Linear)

    val sizeSpec: FiniteAnimationSpec<IntSize> = tween(MEDIUM, easing = Mechanical)
    val offsetSpec: FiniteAnimationSpec<IntOffset> = tween(MEDIUM, easing = Mechanical)

    /**
     * A word or a number that changes, as a readout ticks over: the old one slides up and out, the new one in from
     * below, clipped to its line.
     */
    fun <S> AnimatedContentTransitionScope<S>.tick(): ContentTransform =
        slideInVertically(tween(SHORT, easing = Mechanical)) { it } togetherWith
            slideOutVertically(tween(SHORT, easing = Mechanical)) { -it } using
            SizeTransform(clip = true) { _, _ -> sizeSpec }

    /** A swap that is shown by something else (a tab, a selection): it just changes. */
    fun <S> AnimatedContentTransitionScope<S>.cut(): ContentTransform =
        EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = true) { _, _ -> sizeSpec }

    /** A row, a banner, a section opening: it unrolls from its top, no fade. */
    val reveal: EnterTransition = expandVertically(sizeSpec, expandFrom = androidx.compose.ui.Alignment.Top)

    /** And rolls back up. */
    val conceal: ExitTransition = shrinkVertically(sizeSpec, shrinkTowards = androidx.compose.ui.Alignment.Top)

    /**
     * An indicator's light, blinking in steps rather than breathing: full for the first half of [periodMs], [dim] for
     * the second, starting lit. Read it where it is drawn (`graphicsLayer`, `drawBehind`). Two values a period need no
     * animation: a timed loop flips it, so nothing asks for a frame between the flips.
     */
    @Composable
    fun rememberBlink(periodMs: Int, dim: Float = 0.2f): State<Float> {
        val light = remember { mutableFloatStateOf(1f) }
        LaunchedEffect(periodMs, dim) {
            while (true) {
                light.floatValue = 1f
                delay(periodMs / 2L)
                light.floatValue = dim
                delay(periodMs - periodMs / 2L)
            }
        }
        return light
    }
}
