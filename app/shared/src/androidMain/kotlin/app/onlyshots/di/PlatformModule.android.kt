package app.onlyshots.di

import app.onlyshots.editor.CACHE_DIR
import app.onlyshots.editor.LIBRARY_DIR

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import app.cash.sqldelight.db.SqlDriver
import app.onlyshots.camera.AndroidCameraController
import app.onlyshots.camera.CameraController
import app.onlyshots.core.designsystem.loadOpenSans
import app.onlyshots.storage.LibraryStore
import app.onlyshots.storage.createLibraryDriver
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.ForegroundWork
import app.onlyshots.platform.NsdBrowser
import app.onlyshots.platform.AndroidGallery
import app.onlyshots.platform.VideoGallery
import app.onlyshots.platform.PlatformShare
import app.onlyshots.video.AndroidFrameSampler
import app.onlyshots.video.AndroidVideoProbe
import app.onlyshots.video.VideoProbe
import app.onlyshots.video.FrameSampler
import app.onlyshots.video.Media3VideoExporter
import app.onlyshots.video.VideoExporter
import app.onlyshots.sources.machine.MdnsBrowser
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
actual val platformModule: Module = module {
    single<FileSystem> { FileSystem.SYSTEM }
    single<Path>(named(CACHE_DIR)) { androidContext().cacheDir.toOkioPath() }
    single<Path>(named(LIBRARY_DIR)) { androidContext().filesDir.toOkioPath() / "library" }
    single<SqlDriver> { createLibraryDriver(androidContext()) }
    single<CameraController> { AndroidCameraController(androidContext(), File(androidContext().cacheDir, "camera")) }
    single<VideoExporter> {
        val videosDir = get<LibraryStore>().videosDir
        Media3VideoExporter(
            context = androidContext(),
            videoPath = { item -> if (item.file.startsWith("/")) item.file.toPath() else videosDir / item.file },
            fonts = { loadOpenSans() },
        )
    }
    single<FrameSampler> { AndroidFrameSampler() }
    single<VideoProbe> { AndroidVideoProbe(get()) }
    single<PlatformShare> { AndroidShare(androidContext()) }
    single<BackgroundWork> { ForegroundWork(androidContext()) }
    single<MdnsBrowser> { NsdBrowser(androidContext()) }
    single<VideoGallery> { AndroidGallery(androidContext()) }
}

private class AndroidShare(private val context: Context) : PlatformShare {
    override fun shareVideos(files: List<Path>, onDone: () -> Unit) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.share", File(it.toString())) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        // ClipData as well as the extra: without it the sheet cannot read the file for its
        // preview, and some targets never get the read permission.
        send.setType("video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(context.contentResolver, "video", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // The chooser reports nothing back, so the files stay until ShareTempFiles sweeps them.
    }
}
