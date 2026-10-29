package app.onlyshots.edit

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotFiles
import app.onlyshots.core.model.ShotHistory
import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_export_failed
import app.onlyshots.core.resources.edit_no_pump_at_zero
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.text
import app.onlyshots.export.ExportManager
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.PlatformShare
import app.onlyshots.platform.VideoGallery
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.Library
import app.onlyshots.storage.LibraryStore
import app.onlyshots.storage.SettingsStore
import app.onlyshots.storage.ShareTempFiles
import app.onlyshots.style.StyleLibrary
import app.onlyshots.video.ExportProgress
import app.onlyshots.video.FrameSampler
import app.onlyshots.video.MonoPcm
import app.onlyshots.video.VideoExporter
import app.onlyshots.video.VideoInfo
import app.onlyshots.video.VideoProbe
import app.onlyshots.visualizer.Visualizer
import app.onlyshots.visualizer.VisualizerError
import app.onlyshots.visualizer.VisualizerException
import app.onlyshots.visualizer.VisualizerFetchState
import app.onlyshots.visualizer.VisualizerRef
import app.onlyshots.visualizer.text
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** A probe whose sound is [pcm]: a pump from some point, or nothing at all; a 4K video when [fourK]. */
private class ScriptedProbe(private val pcm: MonoPcm?, private val fourK: Boolean = false) : VideoProbe {
    override suspend fun info(file: Path) =
        if (fourK) VideoInfo(DURATION_US, hasAudio = pcm != null, heightPx = 3840, widthPx = 2160)
        else VideoInfo(DURATION_US, hasAudio = pcm != null, heightPx = 1920)

    override suspend fun writeThumbnail(file: Path, atUs: Long, out: Path, maxWidthPx: Int) = error("no frames here")

    override suspend fun decodeMonoPcm(file: Path, maxSeconds: Int): MonoPcm? = pcm

    override fun supports4k() = fourK
}

private object NoExporter : VideoExporter {
    override fun export(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean,
    ): Flow<ExportProgress> = emptyFlow()
}

/** An exporter that gives up at once, as AVFoundation does on a recording with no frames. */
private object FailingExporter : VideoExporter {
    override fun export(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean,
    ): Flow<ExportProgress> = flowOf(ExportProgress.Rendering(0f), ExportProgress.Failed("no frames"))
}

private const val DURATION_US = 20_000_000L
private const val SAMPLE_RATE = 8_000

/** Room noise, then a 120 Hz pump hum from [onsetS] seconds: what PumpOnsetDetector listens for. */
private fun pump(onsetS: Double): MonoPcm {
    val rnd = Random(42)
    val samples =
        FloatArray(20 * SAMPLE_RATE) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            var v = (rnd.nextFloat() - 0.5f) * 0.01f
            if (t >= onsetS) v += (0.3 * sin(2 * PI * 120 * t) + 0.1 * sin(2 * PI * 240 * t)).toFloat()
            v
        }
    return MonoPcm(samples, SAMPLE_RATE)
}

/** When a shot file is attached, its start is placed: by the pump's sound, else by the clocks, else at 0:00. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditViewModelAttachTest {
    private val fs = FakeFileSystem()
    private val store = LibraryStore(fs, "/library".toPath())
    private val library = Library(store)
    private val shotStarted = Instant.parse("2026-10-18T15:42:59Z")

    /**
     * Main and the view model's work on one test dispatcher: everything runs on the test's thread, nothing after it.
     */
    private val main = UnconfinedTestDispatcher()

    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(main)

    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun video(
        recordedAt: Instant? = null,
        shotStartUs: Long? = null,
        syncLocked: Boolean = false,
    ) =
        VideoItem(
            id = "v",
            file = "v.mp4",
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = DURATION_US,
            origin = Origin.IMPORTED,
            hasAudio = true,
            shot = null,
            shotStartInVideoUs = shotStartUs,
            syncLocked = syncLocked,
            trimInUs = 0,
            trimOutUs = DURATION_US,
            overlay = OverlaySettings(),
            recordedAt = recordedAt,
        )

    private fun viewModel(
        item: VideoItem,
        sound: MonoPcm?,
        fourK: Boolean = false,
        exporter: VideoExporter = NoExporter,
        visualizer: Visualizer? = null,
    ): EditViewModel {
        library.save(item)
        val settings = AppSettingsRepo(SettingsStore(fs, "/library/settings.json".toPath()))
        val styles = StyleLibrary(settings)
        val exports =
            ExportManager(
                store = store,
                exporter = exporter,
                temp = ShareTempFiles(fs, "/cache/share".toPath()),
                share =
                    object : PlatformShare {
                        override fun shareVideos(files: List<Path>, onDone: () -> Unit) = onDone()
                    },
                gallery =
                    object : VideoGallery {
                        override suspend fun save(file: Path) = Unit
                    },
                background =
                    object : BackgroundWork {
                        override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T = block()
                    },
                nowMs = { 0L },
                scope = CoroutineScope(Dispatchers.Unconfined),
                words = { "shot" },
            )
        return EditViewModel(
            id = item.id,
            library = library,
            store = store,
            probe = ScriptedProbe(sound, fourK),
            source = ShotHistory.None,
            exports = exports,
            fs = fs,
            cacheDir = "/cache/edit".toPath(),
            styles = styles,
            settings = settings,
            sampler =
                object : FrameSampler {
                    override suspend fun lumaAt(file: Path, atUs: Long, points: List<Pair<Float, Float>>) = null
                },
            visualizer = visualizer,
            work = main,
        )
    }

    /** Hands the editor a shot file, the newer Gaggiuino shape, and waits until it is attached. */
    private suspend fun attach(vm: EditViewModel): VideoItem {
        vm.ui.first { it.item != null }
        val path = "/picked/shot.json".toPath()
        fs.createDirectories(path.parent!!)
        fs.write(path) {
            writeUtf8(
                """{ "id": 121, "duration": 50, "timestamp": ${shotStarted.epochSeconds},
                    "datapoints": [ { "timeInShot": 0, "pressure": 0.5 }, { "timeInShot": 5000, "pressure": 9 } ],
                    "profile": { "name": "Flimsy Light" } }"""
            )
        }
        vm.importShotFile(path)
        return vm.ui.first { it.item?.shot != null && it.busy == null }.item!!
    }

    @Test
    fun aFourKVideoSavesIn4KSharesIn1080pAndRemembersAPick() = runTest {
        val vm = viewModel(video(), sound = null, fourK = true)
        val ui = vm.ui.first { it.qualities.size == 2 }
        assertEquals(Quality.UHD_4K, ui.galleryQuality)
        assertEquals(Quality.HD_1080, ui.shareQuality)

        vm.pickQuality(ExportManager.Destination.SHARE, Quality.UHD_4K)

        assertEquals(Quality.UHD_4K, vm.ui.value.shareQuality)
        // The next video starts on the pick: it is in the settings file.
        val next = viewModel(video(), sound = null, fourK = true)
        assertEquals(Quality.UHD_4K, next.ui.first { it.qualities.size == 2 }.shareQuality)
    }

    @Test
    fun thePumpPlacesTheStart() = runTest {
        val vm = viewModel(video(), sound = pump(onsetS = 3.0))
        val item = attach(vm)
        val start = item.shotStartInVideoUs!!
        assertTrue(start in 2_900_000L..3_150_000L, "start at $start")
        assertNull(vm.ui.value.message)
    }

    @Test
    fun withoutAPumpTheClocksPlaceIt() = runTest {
        val vm = viewModel(video(recordedAt = shotStarted.minusSeconds(4)), sound = null)
        assertEquals(4_000_000L, attach(vm).shotStartInVideoUs)
        assertNull(vm.ui.value.message)
    }

    @Test
    fun withNeitherTheShotStartsAtZeroAndTheEditorSaysSo() = runTest {
        val vm = viewModel(video(), sound = null)
        assertEquals(0L, attach(vm).shotStartInVideoUs)
        assertEquals(Res.string.edit_no_pump_at_zero.text(), vm.ui.value.message)
    }

    @Test
    fun aPlacedFlagStays() = runTest {
        val vm = viewModel(video(shotStartUs = 7_000_000), sound = pump(onsetS = 3.0))
        assertEquals(7_000_000L, attach(vm).shotStartInVideoUs)
    }

    @Test
    fun aSyncedRecordingKeepsItsStart() = runTest {
        val vm = viewModel(video(shotStartUs = 2_000_000, syncLocked = true), sound = null)
        assertEquals(2_000_000L, attach(vm).shotStartInVideoUs)
        assertNull(vm.ui.value.message)
    }

    @Test
    fun oneUndoTakesBackTheShotAndItsStart() = runTest {
        val vm = viewModel(video(), sound = null)
        attach(vm)
        vm.undo()
        val item = vm.ui.value.item!!
        assertNull(item.shot)
        assertNull(item.shotStartInVideoUs)
    }

    @Test
    fun aNewFormatRefitsTheGraphInOneUndoStep() = runTest {
        val vm = viewModel(video(), sound = null)
        val before = vm.ui.first { it.item != null }.item!!
        vm.setFormat(VideoFormat.SQUARE_1_1)
        val square = vm.ui.value.item!!
        assertEquals(VideoFormat.SQUARE_1_1, square.format)
        assertEquals(before.overlay.rect.refit(VideoFormat.PORTRAIT_9_16, VideoFormat.SQUARE_1_1), square.overlay.rect)
        vm.undo()
        assertEquals(before, vm.ui.value.item)
    }

    @Test
    fun anExportThatFailsAtOnceTakesTheCoverDownAndSaysWhy() = runTest {
        val vm = viewModel(video(), sound = null, exporter = FailingExporter)
        vm.ui.first { it.item != null }
        vm.saveAndExport(Quality.HD_1080, withSound = false, ExportManager.Destination.GALLERY)
        val ui = withTimeout(5_000) { vm.ui.first { it.exporting == null } }
        assertEquals(Res.string.edit_export_failed.text("no frames".raw()), ui.message)
    }

    @Test
    fun anExportThatEndsWithoutAFileTakesTheCoverDown() = runTest {
        val vm = viewModel(video(), sound = null)
        vm.ui.first { it.item != null }
        vm.saveAndExport(Quality.HD_1080, withSound = false, ExportManager.Destination.GALLERY)
        withTimeout(5_000) { vm.ui.first { it.exporting == null } }
    }

    @Test
    fun aPickedShotHandedOverAsJsonIsAttachedOnceTheVideoIsIn() = runTest {
        val vm = viewModel(video(), sound = null)
        vm.attachPickedShot(SidecarCodec.json.encodeToString(Shot.serializer(), SampleShots.zer0))
        val item = vm.ui.first { it.item?.shot != null && it.busy == null }.item!!
        assertEquals(SampleShots.zer0.id, item.shot?.id)
        assertEquals(0L, item.shotStartInVideoUs)
    }

    @Test
    fun aPickedShotThatIsNotAShotIsDropped() = runTest {
        val vm = viewModel(video(), sound = null)
        vm.ui.first { it.item != null }
        vm.attachPickedShot("not a shot")
        assertNull(vm.ui.value.item?.shot)
    }

    @Test
    fun theGraphPageFollowsTheGraphsSheetsAndStaysForTheOthers() = runTest {
        val vm = viewModel(video(), sound = null)
        vm.ui.first { it.item != null }
        vm.openSheet(EditSheet.DATA)
        vm.openSheet(EditSheet.EXPORT)
        assertEquals(EditSheet.DATA, vm.ui.value.graphPage)
        vm.closeSheet()
        assertEquals(EditSheet.DATA, vm.ui.value.graphPage)
    }

    /** A visualizer holding one shot, under the code ABCD; any other code has expired. */
    private class FakeVisualizer : Visualizer {
        val asked = mutableListOf<VisualizerRef>()

        override suspend fun fetch(ref: VisualizerRef): Shot {
            asked += ref
            if (ref != VisualizerRef.Code("ABCD")) throw VisualizerException(VisualizerError.NOT_FOUND)
            return ShotFiles.parse(
                """{ "id": "832f7109", "profile_title": "Zer0", "timeframe": [0, 5],
                      "data": { "espresso_pressure": [0.5, 9] } }""",
                fallbackId = "v",
            )
        }
    }

    @Test
    fun withoutAVisualizerTheWayIsHidden() = runTest {
        val vm = viewModel(video(), sound = null)
        assertEquals(false, vm.ui.first { it.item != null }.hasVisualizer)
    }

    @Test
    fun aShareCodeAttachesTheVisualizersShot() = runTest {
        val visualizer = FakeVisualizer()
        val vm = viewModel(video(), sound = null, visualizer = visualizer)
        vm.ui.first { it.item != null }
        vm.openSheet(EditSheet.SHOT)
        vm.openVisualizer()
        assertEquals(VisualizerFetchState(), vm.ui.value.visualizer)

        vm.fetchFromVisualizer(" abcd ")

        val item = vm.ui.first { it.item?.shot != null && it.busy == null }.item!!
        assertEquals(listOf<VisualizerRef>(VisualizerRef.Code("ABCD")), visualizer.asked)
        assertEquals("visualizer-832f7109", item.shot!!.id)
        assertNull(vm.ui.value.sheet)
        assertNull(vm.ui.value.visualizer)
    }

    @Test
    fun anExpiredCodeIsSaidInTheForm() = runTest {
        val vm = viewModel(video(), sound = null, visualizer = FakeVisualizer())
        vm.ui.first { it.item != null }
        vm.openSheet(EditSheet.SHOT)
        vm.openVisualizer()

        vm.fetchFromVisualizer("ZZZZ")

        val form = vm.ui.first { it.visualizer?.error != null }.visualizer!!
        assertEquals(VisualizerError.NOT_FOUND.text(), form.error)
        assertEquals(EditSheet.SHOT, vm.ui.value.sheet)
        assertNull(vm.ui.value.item!!.shot)
    }

    @Test
    fun somethingElseIsNotAsked() = runTest {
        val visualizer = FakeVisualizer()
        val vm = viewModel(video(), sound = null, visualizer = visualizer)
        vm.ui.first { it.item != null }
        vm.openSheet(EditSheet.SHOT)
        vm.openVisualizer()

        vm.fetchFromVisualizer("not a code")

        assertTrue(vm.ui.value.visualizer?.error != null)
        assertEquals(emptyList(), visualizer.asked)
    }
}

private fun Instant.minusSeconds(s: Long) = Instant.fromEpochSeconds(epochSeconds - s)
