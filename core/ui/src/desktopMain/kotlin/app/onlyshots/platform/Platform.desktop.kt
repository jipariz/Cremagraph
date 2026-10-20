package app.onlyshots.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_pick_shot_dialog
import app.onlyshots.core.resources.core_pick_video_dialog
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.io.File
import okio.Path
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.stringResource

// The desktop development host: permissions are always granted, pickers are AWT file dialogs,
// sharing reveals the file in the system file manager.

@Composable
actual fun rememberBluetoothPermission(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

@Composable
actual fun rememberMediaPermission(onResult: (camera: Boolean, mic: Boolean) -> Unit): () -> Unit = {
    onResult(true, true)
}

@Composable actual fun rememberCameraGranted(): () -> Boolean = { true }

@Composable actual fun rememberOpenAppSettings(): () -> Unit = {}

@Composable actual fun KeepScreenOn(enabled: Boolean) = Unit

@Composable actual fun rememberNotificationPermission(): () -> Unit = {}

@Composable
actual fun rememberShotFilePicker(onPicked: (Path) -> Unit): () -> Unit {
    val title = stringResource(Res.string.core_pick_shot_dialog)
    return { pickFile(title, "*.shot;*.json")?.let(onPicked) }
}

@Composable
actual fun rememberVideoPicker(onPicked: (Path) -> Unit): () -> Unit {
    val title = stringResource(Res.string.core_pick_video_dialog)
    return { pickFile(title, "*.mp4;*.mov")?.let(onPicked) }
}

private fun pickFile(title: String, filter: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply { file = filter }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name).toOkioPath()
}

/**
 * The desktop has no share sheet: the export is kept in Movies/OnlyShots, as a save would, and shown in its folder. The
 * rendered file itself is a temporary one, removed once this returns.
 */
class DesktopShare : PlatformShare {
    override fun shareVideos(files: List<Path>, onDone: () -> Unit) {
        val kept = files.map { keepInMovies(it) }
        val desktop = Desktop.getDesktop()
        kept.firstOrNull()?.let {
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(it)
        }
        onDone()
    }
}

private fun keepInMovies(file: Path): File {
    val dir = File(System.getProperty("user.home"), "Movies/OnlyShots").apply { mkdirs() }
    return file.toFile().copyTo(File(dir, "onlyshots-${System.currentTimeMillis()}.mp4"))
}

class DesktopBackgroundWork : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T = block()
}

/** The development host's stand-in for a gallery: ~/Movies/OnlyShots. */
class DesktopGallery : VideoGallery {
    override suspend fun save(file: Path) {
        keepInMovies(file)
    }
}

@Composable actual fun rememberGalleryAccess(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

/** A video file dropped on the window, from Finder or Explorer: the first .mp4 or .mov among them. */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.videoDrop(onFile: (Path) -> Unit, onHover: (Boolean) -> Unit): Modifier =
    dragAndDropTarget(
        shouldStartDragAndDrop = { true },
        target =
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) = onHover(true)

                override fun onExited(event: DragAndDropEvent) = onHover(false)

                override fun onEnded(event: DragAndDropEvent) = onHover(false)

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    onHover(false)
                    val files = runCatching {
                        @Suppress("UNCHECKED_CAST")
                        event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    }
                        .getOrNull()
                        .orEmpty()
                    val video =
                        files.firstOrNull { it.extension.lowercase() in setOf("mp4", "mov", "m4v") } ?: return false
                    onFile(video.toOkioPath())
                    return true
                }
            },
    )
