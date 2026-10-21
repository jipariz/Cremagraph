package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import okio.Path

/**
 * Returns a function that opens the system picker and calls [onPicked] with a copy of the chosen video in the cache
 * directory. Videos are copied into the app's own library; nothing is written back to Photos. Under a
 * [LocalPickerOverride] (UI tests) the override answers instead of the system picker.
 */
@Composable
fun rememberVideoPicker(onPicked: (Path) -> Unit): () -> Unit {
    val platform = rememberPlatformVideoPicker(onPicked)
    val override = LocalPickerOverride.current ?: return platform
    return { override.pick(PickKind.Video)?.let(onPicked) }
}

/** The platform's own video picker: Photos, the system file picker, a file dialog, or the browser's file input. */
@Composable internal expect fun rememberPlatformVideoPicker(onPicked: (Path) -> Unit): () -> Unit

/**
 * Calls [onFile] with a video file dropped on the window, and [onHover] with whether a file is being dragged over it:
 * the desktop and web editors'; a phone has no window to drop on.
 */
expect fun Modifier.videoDrop(onFile: (Path) -> Unit, onHover: (Boolean) -> Unit = {}): Modifier
