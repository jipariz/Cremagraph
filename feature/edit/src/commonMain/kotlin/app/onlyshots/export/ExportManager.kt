package app.onlyshots.export

import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.core_export_unnamed
import app.onlyshots.core.resources.edit_creating_videos
import app.onlyshots.core.resources.edit_export_failed
import app.onlyshots.core.resources.edit_gallery_failed
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.resolve
import app.onlyshots.core.resources.text
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.ExportEnd
import app.onlyshots.platform.ExportStatus
import app.onlyshots.platform.ExportTask
import app.onlyshots.platform.PlatformShare
import app.onlyshots.platform.SavedVideo
import app.onlyshots.platform.VideoGallery
import app.onlyshots.storage.LibraryStore
import app.onlyshots.storage.ShareTempFiles
import app.onlyshots.video.ExportProgress
import app.onlyshots.video.VideoExporter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

/**
 * Renders videos with their overlay and hands the finished files on: to the system share sheet, or into the phone's
 * gallery. Rendering keeps going while the user is elsewhere in the app or has left it: the platform shows it outside
 * the app ([BackgroundWork]) and says how it ended; the edit screen shows the progress and reads the [outcomes].
 *
 * ponytail: one render at a time. Queue depth only matters if someone shares a dozen videos.
 */
class ExportManager(
    private val store: LibraryStore,
    private val exporter: VideoExporter,
    private val temp: ShareTempFiles,
    private val share: PlatformShare,
    private val gallery: VideoGallery,
    private val background: BackgroundWork,
    private val nowMs: () -> Long,
    private val scope: CoroutineScope,
    /** Puts the keep-alive's title into words; a test hands its own, since resources don't load there. */
    private val words: suspend (UiText) -> String = { it.resolve() },
) {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** videoId → 0..1 of the trimmed duration rendered. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _left = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** videoId → about how many milliseconds are left, once the first few percent give a fair guess. */
    val left: StateFlow<Map<String, Long>> = _left.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val _outcomes = MutableStateFlow<Map<String, Outcome>>(emptyMap())

    /**
     * videoId → how its last render ended, set before its [progress] entry goes and kept until [consume]d: the edit
     * screen, even one opened afresh from a notification, reads it to show the done state or the failure.
     */
    val outcomes: StateFlow<Map<String, Outcome>> = _outcomes.asStateFlow()

    private val lock = Mutex()
    private var running: Job? = null

    /** Stops the render in progress, if any; nothing is shared and its progress entry goes. */
    fun cancel() {
        running?.cancel()
    }

    /** Where a finished render goes. */
    enum class Destination {
        SHARE,
        GALLERY,
    }

    /** How a render ended. */
    sealed interface Outcome {
        /** Handed to [destination]; [saved] finds it in the gallery again, where the platform gives that. */
        data class Done(val destination: Destination, val saved: SavedVideo? = null) : Outcome

        data class Failed(val message: UiText) : Outcome

        data object Cancelled : Outcome
    }

    /** The outcome of [id]'s last render, now handled. */
    fun consume(id: String): Outcome? = _outcomes.value[id].also { _outcomes.update { it - id } }

    /**
     * Renders [ids] one after another and hands the results to [destination]. Each video's progress entry stays until
     * its file has been handed on, and its [outcomes] entry (and any failure in [message]) is set before the entry
     * goes: the edit screen reads the entry's removal as "finished" and the outcome as how it went.
     */
    fun enqueue(ids: List<String>, quality: Quality, withSound: Boolean, destination: Destination = Destination.SHARE) {
        if (ids.isEmpty()) return
        running = scope.launch {
            lock.withLock {
                val ends = ArrayList<ExportEnd>()
                try {
                    temp.cleanup(nowMs())
                    val items = ids.mapNotNull { store.read(it) }
                    if (items.isEmpty()) return@withLock
                    val first = items.first()
                    val task =
                        ExportTask(
                            id = first.id,
                            name =
                                if (items.size == 1) nameOf(first)
                                else words(Res.plurals.edit_creating_videos.text(items.size)),
                            quality = quality.label,
                            onCancel = ::cancel,
                        )
                    background.keepAlive(task) { report ->
                        val files = ArrayList<Pair<VideoItem, Path>>(items.size)
                        for (item in items) {
                            val file = render(item, quality, withSound, report)
                            if (file != null) {
                                files += item to file
                            } else {
                                val failed = _outcomes.value[item.id] as? Outcome.Failed
                                if (failed != null)
                                    ends += ExportEnd.Failed(item.id, nameOf(item), words(failed.message))
                            }
                        }
                        if (files.isEmpty()) return@keepAlive
                        when (destination) {
                            Destination.SHARE -> {
                                files.forEach { (item, _) -> done(item.id, Outcome.Done(destination)) }
                                share.shareVideos(files.map { it.second }) { files.forEach { temp.release(it.second) } }
                            }
                            Destination.GALLERY -> {
                                for ((item, file) in files) {
                                    try {
                                        val saved = gallery.saveVideo(file)
                                        done(item.id, Outcome.Done(destination, saved))
                                        ends +=
                                            ExportEnd.Saved(
                                                item.id,
                                                nameOf(item),
                                                lengthOfUs(item.trimmedDurationUs),
                                                saved,
                                            )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        val message = Res.string.edit_gallery_failed.text("${e.message}".raw())
                                        fail(item.id, message)
                                        ends += ExportEnd.Failed(item.id, nameOf(item), "${e.message}")
                                    }
                                }
                                files.forEach { temp.release(it.second) }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    ids.filterNot { it in _outcomes.value }.forEach { done(it, Outcome.Cancelled) }
                    throw e
                } finally {
                    _progress.update { it - ids.toSet() }
                    _left.update { it - ids.toSet() }
                }
                // Said after the render's notification has gone, so the result is what stays in the shade.
                ends.forEach { background.finished(it) }
            }
        }
    }

    /** One video to [out]: its file, or null when it failed (its outcome and [message] say why). */
    private suspend fun render(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        report: (ExportStatus) -> Unit,
    ): Path? {
        val id = item.id
        val out = temp.newFile(id, nowMs())
        _progress.update { it + (id to 0f) }
        _outcomes.update { it - id }
        val startMs = nowMs()
        var reportedMs: Long? = null
        var file: Path? = null
        var failed: String? = null
        try {
            exporter.export(item, quality, withSound && item.hasAudio, out).collect { p ->
                when (p) {
                    is ExportProgress.Rendering -> {
                        val now = nowMs()
                        // Guessing from the first few percent swings wildly; past that it settles.
                        val leftMs =
                            if (p.fraction >= 0.05f) ((now - startMs) / p.fraction * (1f - p.fraction)).toLong()
                            else null
                        _progress.update { it + (id to p.fraction) }
                        _left.update { if (leftMs != null) it + (id to leftMs) else it - id }
                        // Outside the app about once a second: a notification updated faster is dropped anyway.
                        if (reportedMs.let { it == null || now - it >= REPORT_EVERY_MS }) {
                            reportedMs = now
                            report(ExportStatus(p.fraction, leftMs?.let { lengthOfUs(it * 1_000) }))
                        }
                    }
                    is ExportProgress.Done -> file = p.file
                    is ExportProgress.Failed -> failed = p.reason
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = e.message ?: e::class.simpleName ?: "?"
        }
        failed?.let {
            fail(id, Res.string.edit_export_failed.text(it.raw()))
            _progress.update { all -> all - id }
            return null
        }
        return file
    }

    private fun done(id: String, outcome: Outcome) = _outcomes.update { it + (id to outcome) }

    private fun fail(id: String, message: UiText) {
        _message.value = message
        done(id, Outcome.Failed(message))
    }

    /** What the video is called outside the app: its graph's title, else its shot's profile. */
    private suspend fun nameOf(item: VideoItem): String =
        item.overlay.title?.takeIf { it.isNotBlank() }
            ?: item.shot?.profileName?.takeIf { it.isNotBlank() }
            ?: words(Res.string.core_export_unnamed.text())

    fun clearMessage() {
        _message.value = null
    }

    /** The [saved] video in the gallery's own app. */
    fun open(saved: SavedVideo) {
        gallery.open(saved)
    }

    private companion object {
        const val REPORT_EVERY_MS = 1_000L
    }
}

/** "1080p", "4K": how the notification names the quality. */
private val Quality.label: String
    get() =
        when (this) {
            Quality.HD_1080 -> "1080p"
            Quality.UHD_4K -> "4K"
        }

/** m:ss. */
private fun lengthOfUs(us: Long): String {
    val s = (us / 1_000_000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
