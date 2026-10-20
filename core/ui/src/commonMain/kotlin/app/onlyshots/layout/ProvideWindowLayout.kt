package app.onlyshots.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * Decides the [WindowLayout] for the space [content] gets, from the adaptive library's window classes and posture
 * (hinges on Android foldables, the fold the host reports on iOS), and provides it as [LocalWindowLayout]. Every app
 * shell and the screenshot previews go through this, so a preview at tablet size lays out exactly as a tablet would.
 */
@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val info = platformWindowAdaptiveInfo()
        val layout = windowLayout(info, maxWidth.value, maxHeight.value, LocalDensity.current.density)
        CompositionLocalProvider(LocalWindowLayout provides layout) { content() }
    }
}

/**
 * The adaptive library's `currentWindowAdaptiveInfo()`, with what it cannot see itself added: on iOS, a foldable
 * iPhone's fold.
 */
@Composable expect fun platformWindowAdaptiveInfo(): WindowAdaptiveInfo
