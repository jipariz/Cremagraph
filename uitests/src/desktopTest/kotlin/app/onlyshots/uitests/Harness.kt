package app.onlyshots.uitests

import app.onlyshots.camera.CameraController
import app.onlyshots.camera.CameraState
import app.onlyshots.camera.Recording
import app.onlyshots.core.designsystem.loadOverlayFonts
import app.onlyshots.core.model.ShotHistory
import app.onlyshots.di.appModule
import app.onlyshots.editor.CACHE_DIR
import app.onlyshots.editor.LIBRARY_DIR
import app.onlyshots.editor.editorModule
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.PickKind
import app.onlyshots.platform.PickerOverride
import app.onlyshots.platform.PlatformShare
import app.onlyshots.platform.VideoGallery
import app.onlyshots.storage.LibraryStore
import app.onlyshots.video.FfmpegFrameSampler
import app.onlyshots.video.FfmpegVideoExporter
import app.onlyshots.video.FfmpegVideoProbe
import app.onlyshots.video.FrameSampler
import app.onlyshots.video.VideoExporter
import app.onlyshots.video.VideoProbe
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The video module's test clip: what the camera "records" and what a test picks as its video. */
val testClip: Path =
    checkNotNull(System.getProperty("onlyshots.testClip")) { "run through :uitests:desktopTest" }.toPath()

/**
 * One app under test: its own library and cache in a temp directory, the desktop's real FFmpeg probe, sampler and
 * exporter, and fakes for what has no desktop counterpart (the camera's recording, the gallery, the share sheet).
 * [phone] adds the phones' module (sources, home, record); without it this is the desktop and web editor.
 */
class Harness(private val phone: Boolean) {
    val root: Path = Files.createTempDirectory("onlyshots-uitest").toOkioPath()
    val gallery = RecordingGallery()
    val share = RecordingShare()

    /** A Gaggiuino shot file, as SproFiler saves one, for "pick the shot". */
    val shotFile: Path = (root / "londinium.shot").also { FileSystem.SYSTEM.write(it) { writeUtf8(SHOT_FILE) } }

    /** Answers the app's pickers: the test clip for a video, [shotFile] for a shot. */
    val pickers = PickerOverride { kind ->
        when (kind) {
            PickKind.Video -> testClip
            PickKind.Shot -> shotFile
        }
    }

    fun start() {
        val platform = module {
            single<FileSystem> { FileSystem.SYSTEM }
            single<Path>(named(CACHE_DIR)) { root / "cache" }
            single<Path>(named(LIBRARY_DIR)) { root / "library" }
            single<CameraController> { ClipCamera((root / "camera").toFile()) }
            single<VideoExporter> {
                val videosDir = get<LibraryStore>().videosDir
                FfmpegVideoExporter(
                    videoPath = { item ->
                        if (item.file.startsWith("/")) item.file.toPath() else videosDir / item.file
                    },
                    fonts = { loadOverlayFonts() },
                )
            }
            single<FrameSampler> { FfmpegFrameSampler() }
            single<VideoProbe> { FfmpegVideoProbe() }
            single<PlatformShare> { share }
            single<BackgroundWork> { ImmediateBackgroundWork }
            single<VideoGallery> { gallery }
            if (!phone) single<ShotHistory> { ShotHistory.None }
        }
        startKoin { modules(listOfNotNull(editorModule, if (phone) appModule else null, platform)) }
    }

    fun stop() {
        stopKoin()
        root.toFile().deleteRecursively()
    }
}

/** Saves nothing; keeps what it was given, so a test can see that the export reached the gallery. */
class RecordingGallery : VideoGallery {
    val saved = mutableListOf<Path>()

    override suspend fun save(file: Path) {
        saved += file
    }
}

class RecordingShare : PlatformShare {
    val shared = mutableListOf<Path>()

    override fun shareVideos(files: List<Path>, onDone: () -> Unit) {
        shared += files
        onDone()
    }
}

object ImmediateBackgroundWork : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T = block()
}

/** A camera whose every recording is the test clip: arming starts the clock, stopping copies the clip out. */
class ClipCamera(private val dir: File) : CameraController {
    override val state: StateFlow<CameraState> = MutableStateFlow(CameraState(previewReady = true, micOn = true))
    private var startedNs = 0L
    private var triggerUs: Long? = null

    override fun setZoom(zoom: Float) = Unit

    override fun focusAt(x: Float, y: Float) = Unit

    override fun unlockFocus() = Unit

    override fun setMic(on: Boolean) = Unit

    override fun setFourK(on: Boolean) = Unit

    override suspend fun arm(preRollSeconds: Int) {
        startedNs = System.nanoTime()
        triggerUs = null
    }

    override fun disarm() = Unit

    override fun trigger(): Long = ((System.nanoTime() - startedNs) / 1_000).also { triggerUs = it }

    override suspend fun stop(): Recording {
        dir.mkdirs()
        val out = File(dir, "take-${System.nanoTime()}.mp4")
        testClip.toFile().copyTo(out)
        return Recording(
            out.path,
            (System.nanoTime() - startedNs) / 1_000,
            hasAudio = false,
            triggerVideoUs = triggerUs,
        )
    }

    override fun freeBytes(): Long = Long.MAX_VALUE / 2
}

private const val SHOT_FILE =
    """{
  "id": 42,
  "timestamp": 1760000000,
  "duration": 30,
  "profileName": "Londinium",
  "datapoints": {
    "timeInShot": [0, 10, 20, 30],
    "pressure": [0, 60, 90, 80],
    "pumpFlow": [0, 25, 18, 15],
    "shotWeight": [0, 14, 200, 360],
    "weightFlow": [0, 5, 21, 18],
    "temperature": [930, 931, 928, 927],
    "targetPressure": [90, 90, 90, 90],
    "targetPumpFlow": [20, 20, 20, 20],
    "targetTemperature": [930, 930, 930, 930]
  }
}"""
