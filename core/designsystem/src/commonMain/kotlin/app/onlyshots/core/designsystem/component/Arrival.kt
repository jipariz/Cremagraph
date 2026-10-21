package app.onlyshots.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.onlyshots.core.designsystem.CgMotion

/**
 * False on a list's first frame, true after: rows already there when the screen opens just show, and only rows that
 * arrive later ([Arriving]) animate in. A screenshot sees every row in place.
 */
@Composable
fun rememberListSettled(): Boolean {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    return settled
}

/**
 * A row in a list that grows as things are found (machines, scales, shots): it unrolls from its top, no fade, when it
 * arrives after the list is up ([animate], from [rememberListSettled]). Wrap it in a `key` so it is the same row when
 * the list reorders.
 */
@Composable
fun Arriving(animate: Boolean, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(!animate).apply { targetState = true } }
    AnimatedVisibility(
        state,
        enter = CgMotion.reveal,
        exit = ExitTransition.None,
    ) {
        content()
    }
}
