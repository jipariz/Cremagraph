package app.onlyshots.di

import app.onlyshots.camera.CameraController
import app.onlyshots.camera.DesktopCameraController
import app.onlyshots.core.designsystem.loadOverlayFonts
import app.onlyshots.editor.CACHE_DIR
import app.onlyshots.editor.LIBRARY_DIR
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.DesktopBackgroundWork
import app.onlyshots.platform.DesktopGallery
import app.onlyshots.platform.DesktopShare
import app.onlyshots.platform.PlatformShare
import app.onlyshots.platform.VideoGallery
import app.onlyshots.storage.LibraryStore
import app.onlyshots.video.FfmpegFrameSampler
import app.onlyshots.video.FfmpegVideoExporter
import app.onlyshots.video.FfmpegVideoProbe
import app.onlyshots.video.FrameSampler
import app.onlyshots.video.VideoExporter
import app.onlyshots.video.VideoProbe
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The desktop editor's library lives under ~/.onlyshots-desktop. A library from before the rename, in
 * ~/.cremagraph-desktop, is moved there the first time.
 */
private val root: Path by lazy {
    val home = System.getProperty("user.home").toPath()
    val root = home / ".onlyshots-desktop"
    val legacy = home / ".cremagraph-desktop"
    if (!FileSystem.SYSTEM.exists(root) && FileSystem.SYSTEM.exists(legacy)) FileSystem.SYSTEM.atomicMove(legacy, root)
    root
}

actual val platformModule: Module = module {
    single<FileSystem> { FileSystem.SYSTEM }
    single<Path>(named(CACHE_DIR)) { root / "cache" }
    single<Path>(named(LIBRARY_DIR)) { root / "library" }
    single<CameraController> { DesktopCameraController((root / "cache" / "camera").toFile()) }
    // The desktop editor plays, probes and exports through FFmpeg, drawing frames with the same Skia code as iOS.
    single<VideoExporter> {
        val videosDir = get<LibraryStore>().videosDir
        FfmpegVideoExporter(
            videoPath = { item -> if (item.file.startsWith("/")) item.file.toPath() else videosDir / item.file },
            fonts = { loadOverlayFonts() },
        )
    }
    single<FrameSampler> { FfmpegFrameSampler() }
    single<VideoProbe> { FfmpegVideoProbe() }
    single<PlatformShare> { DesktopShare() }
    single<BackgroundWork> { DesktopBackgroundWork() }
    single<VideoGallery> { DesktopGallery() }
}
