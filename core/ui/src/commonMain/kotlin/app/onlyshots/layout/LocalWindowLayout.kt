package app.onlyshots.layout

import androidx.compose.runtime.compositionLocalOf

/**
 * Not static: posture changes arrive as ordinary state, and a static local would invalidate the whole tree on every
 * tick of a fold animation.
 */
val LocalWindowLayout = compositionLocalOf { WindowLayout(LayoutKind.Phone) }
