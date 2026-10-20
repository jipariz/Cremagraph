package app.onlyshots.platform

import androidx.compose.runtime.Composable
import okio.Path

/**
 * Returns a function that opens the system file picker and calls [onPicked] with a copy of the chosen file in the cache
 * directory. Used for a Gaggiuino `.shot` file exported from SproFiler, the visualizer, or the machine itself.
 */
@Composable expect fun rememberShotFilePicker(onPicked: (Path) -> Unit): () -> Unit
