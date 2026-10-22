package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.onlyshots.video.WebFiles
import app.onlyshots.video.WebMedia
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toPath

// The web editor's platform: the browser's file dialog and drop, downloads for share and gallery, and nothing for the
// machine, the scale or the camera, which the web never asks for.

@Composable
internal actual fun rememberPlatformVideoPicker(onPicked: (Path) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return { scope.launch { WebMedia.pickFile("video/*,.mov,.mp4,.m4v")?.let { onPicked(it.toPath()) } } }
}

/** The page takes a dropped video anywhere; the latest screen to ask is the one that gets it. */
actual fun Modifier.videoDrop(onFile: (Path) -> Unit, onHover: (Boolean) -> Unit): Modifier {
    dropTarget = onFile
    hoverTarget = onHover
    if (!dropListening) {
        dropListening = true
        WebMedia.onDrop(onFile = { path -> dropTarget?.invoke(path.toPath()) }, onHover = { hoverTarget?.invoke(it) })
    }
    return this
}

private var dropTarget: ((Path) -> Unit)? = null
private var hoverTarget: ((Boolean) -> Unit)? = null
private var dropListening = false

/** A shot file is read as text into the session's file system, where the editor reads and then deletes it. */
@Composable
internal actual fun rememberPlatformShotFilePicker(onPicked: (Path) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val picked = WebMedia.pickFile(".shot,.json,application/json") ?: return@launch
            val text = runCatching { WebMedia.readText(picked) }.getOrNull()
            WebMedia.forget(picked)
            if (text != null) {
                val dir = "/picked".toPath()
                WebFiles.fs.createDirectories(dir)
                val path = dir / "shot-${shotFiles++}.json"
                WebFiles.fs.write(path) { writeUtf8(text) }
                onPicked(path)
            }
        }
    }
}

private var shotFiles = 0

/** A download needs no permission. */
@Composable actual fun rememberGalleryAccess(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

@Composable
actual fun rememberBluetoothPermission(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(false) }

@Composable actual fun rememberNotificationPermission(): () -> Unit = {}

@Composable
actual fun rememberMediaPermission(onResult: (camera: Boolean, mic: Boolean) -> Unit): () -> Unit = {
    onResult(false, false)
}

@Composable actual fun rememberCameraGranted(): () -> Boolean = { false }

@Composable actual fun rememberOpenAppSettings(): () -> Unit = {}

@Composable actual fun KeepScreenOn(enabled: Boolean) = Unit

/** "Share" in a browser is the download. */
class WebShare : PlatformShare {
    override fun shareVideos(files: List<Path>, onDone: () -> Unit) {
        for (file in files) {
            WebMedia.download(file.toString(), file.name)
            WebMedia.forget(file.toString())
        }
        onDone()
    }
}

/** The browser's downloads folder is the gallery. */
class WebGallery : VideoGallery {
    override suspend fun save(file: Path) {
        WebMedia.download(file.toString(), file.name)
        WebMedia.forget(file.toString())
    }
}

/** A tab keeps running while it is open; there is nothing to hold on to. */
class WebBackgroundWork : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T = block()
}
