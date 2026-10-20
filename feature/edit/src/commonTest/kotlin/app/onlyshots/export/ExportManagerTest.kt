package app.onlyshots.export

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.edit_creating_videos
import app.onlyshots.core.resources.edit_export_failed
import app.onlyshots.core.resources.edit_gallery_failed
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.text
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.PlatformShare
import app.onlyshots.platform.VideoGallery
import app.onlyshots.storage.LibraryStore
import app.onlyshots.storage.ShareTempFiles
import app.onlyshots.video.ExportProgress
import app.onlyshots.video.VideoExporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

private class FakeExporter(
    /** Progress to emit per video id; anything missing renders successfully. */
    private val script: Map<String, List<ExportProgress>> = emptyMap()
) : VideoExporter {
    val calls = mutableListOf<Triple<String, Quality, Boolean>>()

    override fun export(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean,
    ): Flow<ExportProgress> {
        calls += Triple(item.id, quality, withSound)
        return flow {
            val steps = script[item.id] ?: listOf(ExportProgress.Rendering(0.5f), ExportProgress.Done(out))
            for (step in steps) emit(if (step is ExportProgress.Done) ExportProgress.Done(out) else step)
        }
    }
}

/** Emits one progress value, waits for [resume], then finishes. */
private class PausingExporter(private val fraction: Float, private val resume: CompletableDeferred<Unit>) :
    VideoExporter {
    val calls = mutableListOf<Triple<String, Quality, Boolean>>()

    override fun export(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean,
    ): Flow<ExportProgress> {
        calls += Triple(item.id, quality, withSound)
        return flow {
            emit(ExportProgress.Rendering(fraction))
            resume.await()
            emit(ExportProgress.Done(out))
        }
    }
}

private class FakeShare : PlatformShare {
    var shared: List<Path>? = null
    var sheets = 0

    override fun shareVideos(files: List<Path>, onDone: () -> Unit) {
        shared = files
        sheets++
        onDone()
    }
}

/** A gallery that keeps what it was given, or refuses with [failWith]. */
private class FakeGallery(private val failWith: String? = null) : VideoGallery {
    val saved = mutableListOf<Path>()

    override suspend fun save(file: Path) {
        failWith?.let { error(it) }
        saved += file
    }
}

private class FakeBackgroundWork : BackgroundWork {
    val titles = mutableListOf<String>()
    var closed = 0

    var error: Throwable? = null

    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T {
        titles += title
        try {
            return block()
        } catch (e: Throwable) {
            error = e
            throw e
        } finally {
            closed++
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportManagerTest {
    private val fs = FakeFileSystem()
    private val root = "/library".toPath()
    private val store = LibraryStore(fs, root)
    private val temp = ShareTempFiles(fs, "/cache/share".toPath())
    private val share = FakeShare()
    private var gallery = FakeGallery()
    private val background = FakeBackgroundWork()
    private var now = 1_000_000L

    /** What the manager asked to put into words: resources don't load in a host test. */
    private val spoken = mutableListOf<UiText>()

    private fun item(id: String, hasAudio: Boolean = true) =
        VideoItem(
            id = id,
            file = "$id.mp4",
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = 30_000_000,
            origin = Origin.RECORDED,
            hasAudio = hasAudio,
            shot = null,
            shotStartInVideoUs = null,
            syncLocked = false,
            trimInUs = 0,
            trimOutUs = 30_000_000,
            overlay = OverlaySettings(),
        )

    private fun manager(exporter: VideoExporter, scope: kotlinx.coroutines.CoroutineScope) =
        ExportManager(store, exporter, temp, share, gallery, background, { now }, scope) {
            spoken += it
            "title"
        }

    @Test
    fun progressIsReportedWhileRenderingAndClearedAfterwards() = runTest {
        store.write(item("a"))
        // A render that pauses mid-way: without a suspension point the whole export runs in one
        // dispatch and the StateFlow conflates every intermediate value away.
        val paused = CompletableDeferred<Unit>()
        val exporter = PausingExporter(0.5f, paused)
        val manager = manager(exporter, backgroundScope)

        manager.enqueue(listOf("a"), Quality.HD_1080, withSound = true)
        runCurrent()

        assertEquals(mapOf("a" to 0.5f), manager.progress.value)
        assertEquals(listOf(Triple("a", Quality.HD_1080, true)), exporter.calls)
        assertEquals(0, share.sheets)

        paused.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        assertEquals(emptyMap(), manager.progress.value)
        assertEquals(1, share.sheets)
        assertEquals(listOf(Res.plurals.edit_creating_videos.text(1)), spoken)
    }

    @Test
    fun twoVideosShareInOneSheetWithBothFiles() = runTest {
        store.write(item("a"))
        store.write(item("b"))
        val manager = manager(FakeExporter(), backgroundScope)

        manager.enqueue(listOf("a", "b"), Quality.HD_1080, withSound = false)
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, share.sheets)
        assertEquals(2, share.shared?.size)
        assertEquals(listOf(Res.plurals.edit_creating_videos.text(2)), spoken)
    }

    @Test
    fun aFailedRenderIsReportedAndTheOthersStillShare() = runTest {
        store.write(item("bad"))
        store.write(item("good"))
        val exporter = FakeExporter(mapOf("bad" to listOf(ExportProgress.Failed("codec said no"))))
        val manager = manager(exporter, backgroundScope)

        manager.enqueue(listOf("bad", "good"), Quality.HD_1080, withSound = false)
        runCurrent()
        advanceUntilIdle()

        assertEquals(Res.string.edit_export_failed.text("codec said no".raw()), manager.message.value)
        assertEquals(1, share.shared?.size)
        assertEquals(emptyMap(), manager.progress.value)
        // keepAlive must close even when a render fails, or the service would run forever.
        assertEquals(1, background.closed)
    }

    @Test
    fun toTheGalleryTheRenderIsSavedReleasedAndNeverShared() = runTest {
        store.write(item("a"))
        val manager = manager(FakeExporter(), backgroundScope)

        manager.enqueue(listOf("a"), Quality.HD_1080, withSound = false, ExportManager.Destination.GALLERY)
        runCurrent()
        advanceUntilIdle()

        val file = gallery.saved.single()
        assertEquals(0, share.sheets)
        assertFalse(fs.exists(file), "temp file still on disk after the gallery took it")
        assertEquals(null, manager.message.value)
    }

    @Test
    fun aGalleryThatRefusesIsReported() = runTest {
        store.write(item("a"))
        gallery = FakeGallery(failWith = "no space")
        val manager = manager(FakeExporter(), backgroundScope)

        manager.enqueue(listOf("a"), Quality.HD_1080, withSound = false, ExportManager.Destination.GALLERY)
        runCurrent()
        advanceUntilIdle()

        assertEquals(Res.string.edit_gallery_failed.text("no space".raw()), manager.message.value)
        assertEquals(0, share.sheets)
    }

    @Test
    fun theSharedFilesAreReleasedWhenTheSheetIsDone() = runTest {
        store.write(item("a"))
        val manager = manager(FakeExporter(), backgroundScope)

        manager.enqueue(listOf("a"), Quality.HD_1080, withSound = false)
        runCurrent()
        advanceUntilIdle()

        val file = share.shared!!.single()
        assertFalse(fs.exists(file), "temp file still on disk after the share finished")
    }

    @Test
    fun soundIsOffWhenTheVideoHasNone() = runTest {
        store.write(item("silent", hasAudio = false))
        val exporter = FakeExporter()
        val manager = manager(exporter, backgroundScope)

        manager.enqueue(listOf("silent"), Quality.UHD_4K, withSound = true)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf(Triple("silent", Quality.UHD_4K, false)), exporter.calls)
    }

    @Test
    fun nothingHappensWithoutIdsOrASidecar() = runTest {
        val exporter = FakeExporter()
        val manager = manager(exporter, backgroundScope)

        manager.enqueue(emptyList(), Quality.HD_1080, withSound = false)
        manager.enqueue(listOf("never-imported"), Quality.HD_1080, withSound = false)
        runCurrent()
        advanceUntilIdle()

        assertTrue(exporter.calls.isEmpty())
        assertEquals(0, share.sheets)
        // The empty enqueue returns before keepAlive; the missing sidecar is skipped inside it.
        assertEquals(1, background.titles.size)
    }

    @Test
    fun staleShareCopiesAreSweptBeforeRendering() = runTest {
        val old = temp.newFile("gone", 0)
        fs.write(old) { writeUtf8("x") }
        // FakeFileSystem stamps writes with the real clock, so the test clock is moved past it
        // rather than the file being backdated.
        now = fs.metadata(old).lastModifiedAtMillis!! + ShareTempFiles.MAX_AGE_MS + 1
        store.write(item("a"))
        val manager = manager(FakeExporter(), backgroundScope)

        manager.enqueue(listOf("a"), Quality.HD_1080, withSound = false)
        runCurrent()
        advanceUntilIdle()

        assertFalse(fs.exists(old), "a share copy older than a day survived the sweep")
    }

    @Test
    fun twoEnqueuesDoNotInterleave() = runTest {
        store.write(item("a"))
        store.write(item("b"))
        val exporter =
            FakeExporter(
                mapOf(
                    "a" to
                        listOf(
                            ExportProgress.Rendering(0.1f),
                            ExportProgress.Rendering(0.9f),
                            ExportProgress.Done("/x".toPath()),
                        ),
                    "b" to listOf(ExportProgress.Done("/x".toPath())),
                )
            )
        val manager = manager(exporter, backgroundScope)

        manager.enqueue(listOf("a"), Quality.HD_1080, withSound = false)
        manager.enqueue(listOf("b"), Quality.HD_1080, withSound = false)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), exporter.calls.map { it.first })
        assertEquals(2, share.sheets)
    }
}
