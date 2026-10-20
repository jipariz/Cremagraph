package app.onlyshots.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * How things move: one set of durations and curves (Material 3's emphasized and standard), so a toggle, a sheet and a
 * screen change feel like one app. Every animated value starts at its target, so screenshots show the settled state.
 */
object CgMotion {
    /** Colour, a knob, a pill: small things that change in place. */
    const val SHORT = 150
    /** Something appearing or leaving: a chip, a row, a banner. */
    const val MEDIUM = 250
    /** Sheets and screens. */
    const val LONG = 400

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT, easing = Standard)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Standard)

    fun <T> long(): FiniteAnimationSpec<T> = tween(LONG, easing = Emphasized)

    /** Things that settle on a target: a knob, a snapped edge, a playhead. No bounce: the target is exact. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

    /**
     * A layout settling into a new shape (a format picked, a panel moving): about 300 ms and no overshoot, so a picture
     * growing towards a panel never runs past it.
     */
    fun <T> layout(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

    /** A little give, for the one thing the hand just did: the record button, a dropped graph. */
    fun <T> springy(): FiniteAnimationSpec<T> = spring(0.7f, Spring.StiffnessMedium)

    val sizeSpec: FiniteAnimationSpec<IntSize> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
    val offsetSpec: FiniteAnimationSpec<IntOffset> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
}
