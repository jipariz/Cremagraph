package app.onlyshots.platform

import androidx.compose.runtime.Composable
import okio.Path

/**
 * Returns a function that opens the system file picker and calls [onPicked] with a copy of the chosen file in the cache
 * directory. Used for a Gaggiuino `.shot` file exported from SproFiler, the visualizer, or the machine itself. Under a
 * [LocalPickerOverride] (UI tests) the override answers instead of the system picker.
 */
@Composable
fun rememberShotFilePicker(onPicked: (Path) -> Unit): () -> Unit {
    val platform = rememberPlatformShotFilePicker(onPicked)
    val override = LocalPickerOverride.current ?: return platform
    return { override.pick(PickKind.Shot)?.let(onPicked) }
}

/** The platform's own file picker for a shot file. */
@Composable internal expect fun rememberPlatformShotFilePicker(onPicked: (Path) -> Unit): () -> Unit
