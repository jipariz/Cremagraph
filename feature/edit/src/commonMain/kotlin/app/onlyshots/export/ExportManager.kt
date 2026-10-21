package app.onlyshots.export

import app.onlyshots.core.model.Quality
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.edit_creating_videos
import app.onlyshots.core.resources.edit_export_failed
import app.onlyshots.core.resources.edit_gallery_failed
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.resolve
import app.onlyshots.core.resources.text
import app.onlyshots.platform.BackgroundWork
import app.onlyshots.platform.PlatformShare
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
 * gallery. Rendering keeps going while the user is elsewhere in the app or has left it; the edit screen shows the
 * progress.
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

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

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

    /**
     * Renders [ids] one after another and hands the results to [destination]. Each video's progress entry stays until
     * its file has been handed on, and any failure is in [message] before the entry goes: the edit screen reads the
     * entry's removal as "finished" and [message] as how it went, so a failure reported later would read as success.
     */
    fun enqueue(ids: List<String>, quality: Quality, withSound: Boolean, destination: Destination = Destination.SHARE) {
        if (ids.isEmpty()) return
        running = scope.launch {
            lock.withLock {
                try {
                    temp.cleanup(nowMs())
                    background.keepAlive(words(Res.plurals.edit_creating_videos.text(ids.size))) {
                        val files = ArrayList<Path>(ids.size)
                        for (id in ids) {
                            val item = store.read(id) ?: continue
                            val out = temp.newFile(id, nowMs())
                            _progress.update { it + (id to 0f) }
                            var failed: String? = null
                            try {
                                exporter.export(item, quality, withSound && item.hasAudio, out).collect { p ->
                                    when (p) {
                                        is ExportProgress.Rendering -> _progress.update { it + (id to p.fraction) }
                                        is ExportProgress.Done -> files += p.file
                                        is ExportProgress.Failed -> failed = p.reason
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failed = e.message ?: e::class.simpleName ?: "?"
                            }
                            failed?.let {
                                _message.value = Res.string.edit_export_failed.text(it.raw())
                                _progress.update { all -> all - id }
                            }
                        }
                        if (files.isEmpty()) return@keepAlive
                        when (destination) {
                            Destination.SHARE -> share.shareVideos(files) { files.forEach(temp::release) }
                            Destination.GALLERY -> {
                                for (file in files) {
                                    runCatching { gallery.save(file) }
                                        .onFailure {
                                            if (it is CancellationException) throw it
                                            _message.value = Res.string.edit_gallery_failed.text("${it.message}".raw())
                                        }
                                }
                                files.forEach(temp::release)
                            }
                        }
                    }
                } finally {
                    _progress.update { it - ids.toSet() }
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
