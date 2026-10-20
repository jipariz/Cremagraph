package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import okio.Path

/**
 * Returns a function that opens the system picker and calls [onPicked] with a copy of the chosen video in the cache
 * directory. Videos are copied into the app's own library; nothing is written back to Photos.
 */
@Composable expect fun rememberVideoPicker(onPicked: (Path) -> Unit): () -> Unit

/**
 * Calls [onFile] with a video file dropped on the window, and [onHover] with whether a file is being dragged over it:
 * the desktop and web editors'; a phone has no window to drop on.
 */
expect fun Modifier.videoDrop(onFile: (Path) -> Unit, onHover: (Boolean) -> Unit = {}): Modifier
