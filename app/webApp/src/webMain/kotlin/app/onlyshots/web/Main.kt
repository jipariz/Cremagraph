package app.onlyshots.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.onlyshots.core.designsystem.loadOpenSans
import app.onlyshots.core.model.ShotHistory
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_web_file_gone
import app.onlyshots.core.resources.core_web_header_note
import app.onlyshots.core.resources.core_web_videos_note
import app.onlyshots.edit.ExportWording
import app.onlyshots.editor.CACHE_DIR
import app.onlyshots.editor.EditorApp
import app.onlyshots.editor.LIBRARY_DIR
import app.onlyshots.editor.editorModule
import app.onlyshots.open.OpenHost
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.PlatformShare
import app.onlyshots.platform.VideoGallery
import app.onlyshots.platform.WebBackgroundWork
import app.onlyshots.platform.WebGallery
import app.onlyshots.platform.WebShare
import app.onlyshots.storage.LibraryStore
import app.onlyshots.storage.VideoImport
import app.onlyshots.video.FrameSampler
import app.onlyshots.video.VideoExporter
import app.onlyshots.video.VideoProbe
import app.onlyshots.video.WebFiles
import app.onlyshots.video.WebFrameSampler
import app.onlyshots.video.WebMedia
import app.onlyshots.video.WebVideoExporter
import app.onlyshots.video.WebVideoProbe
import kotlinx.browser.document
import kotlinx.browser.window
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The web editor: the same Compose editor as the desktop's (open a video, add its shot file, place the graph, export),
 * compiled to wasm. Session only: the library lives in memory and is gone after a reload. `media.js` is the browser's
 * half, for the picked files, playback, decoding and encoding.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(editorModule, webModule) }
    // media.js is a module, so it runs after this script; nothing asks it for anything until the first tap, but the
    // first screen waits for it anyway so a quick tap cannot find it missing.
    whenMediaReady {
        ComposeViewport(document.body!!) {
            val fileGone = stringResource(Res.string.core_web_file_gone)
            SideEffect { WebWords.fileGone = fileGone }
            // The page's own splash (index.html) has been drawing until now: the editor is on screen, it can go.
            LaunchedEffect(Unit) {
                withFrameNanos {}
                splashDone()
            }
            EditorApp(
                ExportWording.Web,
                host =
                    OpenHost(
                        headerNote = stringResource(Res.string.core_web_header_note),
                        videosNote = stringResource(Res.string.core_web_videos_note),
                    ),
                // The app does more (it films the shot with its graph live): its store links.
                stores = rememberStoreLinks(),
                // The browser's back button walks the editor's screens.
                history = { backStack -> BrowserHistory(backStack) },
            )
        }
    }
}

/** Tells index.html's splash that the editor has drawn its first frame. */
private fun splashDone(): Unit = js("globalThis.onlyshotsReady && globalThis.onlyshotsReady()")

private val webModule = module {
    single<FileSystem> { WebFiles.fs }
    single<Path>(named(CACHE_DIR)) { "/cache".toPath() }
    single<Path>(named(LIBRARY_DIR)) { "/library".toPath() }
    single<ShotHistory> { ShotHistory.None }
    // A picked video stays the browser's File: the library gets its name and an empty stand-in, not a copy.
    single<VideoImport> {
        val store = get<LibraryStore>()
        val fs = get<FileSystem>()
        VideoImport { source, extension ->
            val id = store.newId()
            val name = "$id.$extension"
            val target = store.videosDir / name
            check(WebMedia.alias(source.toString(), target.toString())) { WebWords.fileGone }
            WebMedia.forget(source.toString())
            fs.createDirectories(store.videosDir)
            fs.write(target) {}
            id to name
        }
    }
    single<VideoExporter> {
        val store = get<LibraryStore>()
        WebVideoExporter(videoPath = { store.videoPath(it) }, fonts = { loadOpenSans() })
    }
    single<VideoProbe> { WebVideoProbe(get()) }
    single<FrameSampler> { WebFrameSampler() }
    single<PlatformShare> { WebShare() }
    single<VideoGallery> { WebGallery() }
    single<BackgroundWork> { WebBackgroundWork() }
}

/**
 * Words that code outside composition shows: [VideoImport] is not suspending, so it cannot read a resource itself. The
 * editor's composition sets them, in the user's language, before anything can be picked.
 */
private object WebWords {
    var fileGone = ""
}

private fun whenMediaReady(block: () -> Unit) {
    if (mediaReady()) block()
    else
        window.setTimeout(
            {
                whenMediaReady(block)
                null
            },
            20,
        )
}

private fun mediaReady(): Boolean = js("typeof globalThis.cg === 'object'")
