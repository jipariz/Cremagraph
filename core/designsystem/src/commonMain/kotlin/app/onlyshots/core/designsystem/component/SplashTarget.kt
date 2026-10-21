package app.onlyshots.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * Where the splash's mark lands: the first screen's own [OnlyShotsMark] says where it is, in the root's coordinates,
 * and stays invisible while [hidden] so the two marks are never seen at once.
 */
class SplashTarget {
    var bounds by mutableStateOf<Rect?>(null)
    var hidden by mutableStateOf(false)
}

/** The splash in progress, if any: null in previews and tests, so marks draw as always. */
val LocalSplashTarget = staticCompositionLocalOf<SplashTarget?> { null }
