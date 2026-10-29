package app.onlyshots.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * Decides the [WindowLayout] for the space [content] gets, from the adaptive library's window classes and posture
 * (hinges on Android foldables, the fold the host reports on iOS), and provides it as [LocalWindowLayout]. Every app
 * shell and the phone and tablet screenshot previews go through this, so a preview at tablet size lays out exactly as a
 * tablet would; the postures a canvas can't have (a fold, a tabletop, an upright tablet) are built by the same
 * factories [windowLayout] uses ([bookLayout], [stackedLayout], [tinyLayout], [wideLayout]).
 */
@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val info = platformWindowAdaptiveInfo()
        val layout =
            LocalWindowLayoutOverride.current?.invoke(maxWidth.value, maxHeight.value)
                ?: windowLayout(info, maxWidth.value, maxHeight.value, LocalDensity.current.density)
        CompositionLocalProvider(LocalWindowLayout provides layout) { content() }
    }
}

/**
 * Decides the layout in place of the window (its width and height in dp in, the layout out): UI tests provide one to
 * run the real app shell as a book fold, a tabletop or a cover screen, which a desktop test window cannot be. Null, the
 * window decides, everywhere in the apps.
 */
val LocalWindowLayoutOverride = staticCompositionLocalOf<((widthDp: Float, heightDp: Float) -> WindowLayout)?> { null }

/**
 * The adaptive library's `currentWindowAdaptiveInfo()`, with what it cannot see itself added: on iOS, a foldable
 * iPhone's fold.
 */
@Composable expect fun platformWindowAdaptiveInfo(): WindowAdaptiveInfo
