package app.onlyshots.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgMotion

/**
 * [target], moving to each new value animated when [animateOn] changed with it (a format picked) and at once otherwise
 * (the window turned or resized), as [WidePanes] moves its panes: a picture grows with its panel, never past it.
 */
@Composable
fun animateSizeOn(target: DpSize, animateOn: Any?): DpSize {
    // In dp, as a plain size: there is no converter for DpSize.
    val goal = Size(target.width.value, target.height.value)
    val size = remember { Animatable(goal, Size.VectorConverter) }
    val motion = remember { KeyedMotion(animateOn) }
    LaunchedEffect(goal, animateOn) {
        motion.move(animateOn, { size.animateTo(goal, CgMotion.layout()) }) { size.snapTo(goal) }
    }
    return DpSize(size.value.width.dp, size.value.height.dp)
}

/**
 * Whether a move animates: when the key changed, and for as long as that animation has not settled, so a target that
 * keeps moving under it (a picture in a pane that is itself still sliding) is followed smoothly rather than snapped to.
 */
internal class KeyedMotion(private var key: Any?) {
    private var settling = false

    suspend fun move(newKey: Any?, animate: suspend () -> Unit, snap: suspend () -> Unit) {
        if (key != newKey || settling) {
            key = newKey
            settling = true
            animate()
            // Only reached when the animation ran its course: one cut short by a new target leaves this set.
            settling = false
        } else {
            snap()
        }
    }
}
