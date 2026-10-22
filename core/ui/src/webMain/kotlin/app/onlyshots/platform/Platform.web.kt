package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.onlyshots.video.WebFiles
import app.onlyshots.video.WebMedia
import kotlinx.browser.document
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

actual val exportsNotify: Boolean = true

/** The browser's own prompt; a browser without notifications answers no. */
@Composable
actual fun rememberNotificationPermission(onAnswer: (granted: Boolean) -> Unit): () -> Unit = {
    askToNotify { onAnswer(it) }
}

private fun askToNotify(done: (Boolean) -> Unit): Unit =
    js(
        "(function(){ if (typeof Notification === 'undefined') { done(false); return; } " +
            "Notification.requestPermission().then(function(p){ done(p === 'granted'); }, function(){ done(false); }); })()"
    )

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

/**
 * A tab keeps running while it is open; there is nothing to hold on to, only things to say. While a video is made the
 * tab's title shows how far it is and leaving the page asks first; when it ends in a tab the user is not looking at, a
 * desktop notification says so, and clicking it brings the tab back.
 */
class WebBackgroundWork(
    /** The notification's title and line for how [ExportEnd] went, in words; null says nothing. */
    private val words: suspend (ExportEnd) -> Pair<String, String>? = { null }
) : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T = block()

    override suspend fun <T> keepAlive(task: ExportTask, block: suspend (report: (ExportStatus) -> Unit) -> T): T {
        val title = document.title
        guardLeaving(true)
        return try {
            block { status -> document.title = "${(status.fraction * 100).toInt()}% · $title" }
        } finally {
            guardLeaving(false)
            document.title = title
        }
    }

    override suspend fun finished(end: ExportEnd) {
        val (title, body) = words(end) ?: return
        notifyIfAway(title, body)
    }
}

private fun guardLeaving(on: Boolean): Unit =
    js(
        "(function(){ if (on) { globalThis.onlyshotsLeave = function(e){ e.preventDefault(); e.returnValue = ''; }; " +
            "window.addEventListener('beforeunload', globalThis.onlyshotsLeave); } " +
            "else if (globalThis.onlyshotsLeave) { window.removeEventListener('beforeunload', globalThis.onlyshotsLeave); " +
            "globalThis.onlyshotsLeave = null; } })()"
    )

private fun notifyIfAway(title: String, body: String): Unit =
    js(
        "(function(){ if (typeof Notification === 'undefined' || Notification.permission !== 'granted' || " +
            "!document.hidden) return; try { var n = new Notification(title, { body: body, icon: 'icon-192.png' }); " +
            "n.onclick = function(){ window.focus(); n.close(); }; } catch (e) {} })()"
    )
