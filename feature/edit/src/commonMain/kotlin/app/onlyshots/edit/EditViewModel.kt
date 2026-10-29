package app.onlyshots.edit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.PumpOnsetDetector
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Reframe
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotFiles
import app.onlyshots.core.model.ShotHistory
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.edit_clocks_disagree
import app.onlyshots.core.resources.edit_finding_start
import app.onlyshots.core.resources.edit_lining_up
import app.onlyshots.core.resources.edit_loading_shot
import app.onlyshots.core.resources.edit_no_pump_at_zero
import app.onlyshots.core.resources.edit_no_pump_by_hand
import app.onlyshots.core.resources.edit_not_a_shot_file
import app.onlyshots.core.resources.edit_reading_file
import app.onlyshots.core.resources.edit_shot_load_failed
import app.onlyshots.core.resources.edit_start_unlocked
import app.onlyshots.core.resources.edit_video_gone
import app.onlyshots.core.resources.text
import app.onlyshots.export.ExportManager
import app.onlyshots.export.exportQuality
import app.onlyshots.overlay.GraphStyle
import app.onlyshots.platform.SavedVideo
import app.onlyshots.platform.exportsNotify
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.Library
import app.onlyshots.storage.LibraryStore
import app.onlyshots.style.StyleLibrary
import app.onlyshots.video.FrameSampler
import app.onlyshots.video.LumaTrack
import app.onlyshots.video.VideoProbe
import app.onlyshots.video.availableQualities
import app.onlyshots.video.sourceMapping
import app.onlyshots.visualizer.Visualizer
import app.onlyshots.visualizer.VisualizerFetchState
import app.onlyshots.visualizer.VisualizerFetcher
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.jetbrains.compose.resources.decodeToImageBitmap

enum class EditSheet {
    STYLE,
    DATA,
    SHOT,
    EXPORT,
}

data class EditUiState(
    val item: VideoItem? = null,
    val style: GraphStyle? = null,
    val shots: List<ShotSummary> = emptyList(),
    /** Whether there is a machine to pick a shot from; without one (the desktop, the web) a shot comes from a file. */
    val hasShotHistory: Boolean = false,
    val qualities: List<Quality> = listOf(Quality.HD_1080),
    /** What saving to the gallery makes, out of [qualities]: the last pick, else the best. */
    val galleryQuality: Quality = Quality.HD_1080,
    /** What a share makes, out of [qualities]: the last pick, else 1080p. */
    val shareQuality: Quality = Quality.HD_1080,
    /** The quality of the export running or just done, for its progress and result to name. */
    val exportingQuality: Quality? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** The source picture's width over height as shown, once read: how far it reaches past the frame. */
    val sourceAspect: Float? = null,
    val sheet: EditSheet? = null,
    /** The graph's page (style or data) shown last: a wide panel stays on it while the export borrows the sheet. */
    val graphPage: EditSheet = EditSheet.STYLE,
    /** Whether a shot can be fetched from visualizer.coffee here. */
    val hasVisualizer: Boolean = false,
    /** The shot sheet shows the Visualizer's code-or-link form instead of its choices, in this state. */
    val visualizer: VisualizerFetchState? = null,
    val busy: UiText? = null,
    val message: UiText? = null,
    /** Frames across the clip for the filmstrip, in order; empty until they are all made. */
    val thumbnails: List<ImageBitmap> = emptyList(),
    /** The filmstrip's frames are being made: it shimmers meanwhile. */
    val stripLoading: Boolean = false,
    /** Brightness behind the graph over time, when the style adapts to the video. */
    val luma: LumaTrack? = null,
    /** Loudness from 0 to 1 in even steps across the clip, for the waveform under the strip. */
    val waveform: FloatArray? = null,
    /** Where the pump is heard starting, in video time; null without sound or without a pump. */
    val pumpUs: Long? = null,
    /** 0..1 while this video is being rendered for export. */
    val exporting: Float? = null,
    /** Milliseconds of rendering left, once enough of it has run to tell. */
    val exportLeftMs: Long? = null,
    /** The export finished and went to the share sheet: the screen moves on to the next shot. */
    val exported: Boolean = false,
    /** The export finished and is in the gallery: the export sheet says so and offers the next shot. */
    val savedToGallery: Boolean = false,
    /** Saved to the gallery or shared at least once: leaving no longer loses anything. */
    val kept: Boolean = false,
    /** The video just saved, where the gallery gives a way back to it: the done state offers to open it there. */
    val savedVideo: SavedVideo? = null,
    /** The export screen asks, once, whether to say outside the app when the export is done. */
    val askExportNotifications: Boolean = false,
)

class EditViewModel(
    private val id: String,
    private val library: Library,
    private val store: LibraryStore,
    private val probe: VideoProbe,
    private val source: ShotHistory,
    private val exports: ExportManager,
    private val fs: FileSystem,
    private val cacheDir: Path,
    val styles: StyleLibrary,
    private val settings: AppSettingsRepo,
    private val sampler: FrameSampler,
    /** visualizer.coffee, for a shot shared there; null hides the way. */
    visualizer: Visualizer? = null,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Where the reading, decoding and saving run: a test hands its own dispatcher so nothing outlives it. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel(), EditActions {
    private var state: EditState? = null
    private var saveJob: Job? = null

    val ui: StateFlow<EditUiState>
        field =
            MutableStateFlow(
                EditUiState(hasShotHistory = source !== ShotHistory.None, hasVisualizer = visualizer != null)
            )

    private val fetcher = visualizer?.let { VisualizerFetcher(it, viewModelScope) }

    val videoPath: Path?
        get() = ui.value.item?.let { store.videoPath(it) }

    init {
        fetcher?.let { f ->
            viewModelScope.launch {
                f.state.collect { fetch ->
                    ui.update { if (it.visualizer == null) it else it.copy(visualizer = fetch) }
                }
            }
        }
        viewModelScope.launch {
            val item = withContext(work) { library.load(id) }
            if (item == null) {
                ui.update { it.copy(message = Res.string.edit_video_gone.text()) }
                return@launch
            }
            state = EditState(item)
            publish()
            // The machine's shots, for the shot pickers to list under the file.
            if (ui.value.hasShotHistory) loadShots()
            val info = withContext(work) { runCatching { probe.info(store.videoPath(item)) }.getOrNull() }
            sourceAspect = info?.aspect
            val qualities = availableQualities(info?.shortSidePx ?: 0, probe.supports4k())
            ui.update { it.copy(qualities = qualities, sourceAspect = info?.aspect).withExportQualities() }
            analyse(item)
            // A shot handed over with the video but not placed (the phones' "compose a video") is lined up as one
            // picked
            // here would be; not an undo step, since the video never was without it here.
            val unplaced = item.shot
            if (unplaced != null && item.shotStartInVideoUs == null && !item.syncLocked) {
                attach(unplaced, push = false)
                ui.update { it.copy(busy = null) }
            }
        }
        viewModelScope.launch {
            // Watched with the outcomes, not only the progress: a render that fails at once can add and drop its
            // progress entry between two looks, and the map, back as it was, is never seen to change.
            combine(exports.progress, exports.left, exports.outcomes) { all, left, outcomes ->
                    Triple(all[id], left[id], id in outcomes)
                }
                .collect { (p, left, over) ->
                    if (p != null) {
                        ui.update { it.copy(exporting = p, exportLeftMs = left) }
                    } else if (over) {
                        // Over: here, or before this screen was opened (from the export's notification).
                        exports.consume(id)?.let(::ended)
                    }
                }
        }
    }

    /** The export finished, failed or was cancelled; the manager kept which, and where it went. */
    private fun ended(outcome: ExportManager.Outcome) {
        exports.clearMessage()
        ui.update {
            when (outcome) {
                is ExportManager.Outcome.Done ->
                    it.copy(
                        exporting = null,
                        exportLeftMs = null,
                        exported = outcome.destination == ExportManager.Destination.SHARE,
                        savedToGallery = outcome.destination == ExportManager.Destination.GALLERY,
                        sheet =
                            if (outcome.destination == ExportManager.Destination.GALLERY) EditSheet.EXPORT
                            else it.sheet,
                        savedVideo = outcome.saved,
                        kept = true,
                    )
                is ExportManager.Outcome.Failed ->
                    it.copy(exporting = null, exportLeftMs = null, message = outcome.message)
                ExportManager.Outcome.Cancelled -> it.copy(exporting = null, exportLeftMs = null)
            }
        }
    }

    override fun trim(inUs: Long, outUs: Long, push: Boolean) = edit(push) { it.trimmed(inUs, outUs) }

    override fun setShotStart(videoUs: Long, push: Boolean) = edit(push) { it.withShotStart(videoUs) }

    override fun unlockShotStart() {
        if (state?.item?.syncLocked != true) return
        edit(push = true) { it.unlocked() }
        ui.update { it.copy(message = Res.string.edit_start_unlocked.text()) }
    }

    override fun overlay(push: Boolean, change: (OverlaySettings) -> OverlaySettings) =
        edit(push) { it.copy(overlay = change(it.overlay)) }

    /** The video switched to style [id]; a user style is copied into it so the video keeps its look. */
    override fun pickStyle(id: String) = overlay { styles.pick(it, id) }

    override fun renameStyle(id: String, name: String) {
        styles.rename(id, name)
        styleSaved(id)
    }

    override fun deleteStyle(id: String) = styles.delete(id)

    /** The style editor saved [id]: if this video uses it, its copy follows the change. */
    fun styleSaved(id: String) {
        val s = state ?: return
        if (s.item.overlay.styleId == id) overlay(push = false) { styles.pick(it, id) } else publish()
    }

    override fun undo() {
        state?.undo()
        publish()
        scheduleSave()
    }

    override fun redo() {
        state?.redo()
        publish()
        scheduleSave()
    }

    override fun setFormat(format: VideoFormat) =
        edit(push = true) {
            if (it.format == format) it
            else
                it.copy(
                    format = format,
                    overlay = it.overlay.copy(rect = it.overlay.rect.refit(it.format, format)),
                    reframe = it.reframe.clamped(sourceAspect, format),
                )
        }

    override fun reframe(push: Boolean, change: (Reframe) -> Reframe) =
        edit(push) { it.copy(reframe = change(it.reframe).clamped(sourceAspect, it.format)) }

    override fun matchByClock() {
        val item = state?.item ?: return
        if (item.syncLocked) return
        val start = item.shotStartByClock()
        if (start == null) {
            ui.update { it.copy(message = Res.string.edit_clocks_disagree.text()) }
        } else {
            edit(push = true) { it.withShotStart(start) }
        }
    }

    /**
     * Attaches [shot] and, on a video not lined up yet, places its start: where the pump is heard, else where the
     * clocks put it, else at 0:00, so an attached shot always has a start and the export always carries the graph. A
     * synced recording, or a video whose flag is already placed, keeps its flag. One step, so one undo takes back both.
     */
    private suspend fun attach(shot: Shot, push: Boolean = true) {
        val item = state?.item ?: return
        val placed =
            if (item.syncLocked || item.shotStartInVideoUs != null) null
            else {
                ui.update { it.copy(busy = Res.string.edit_lining_up.text()) }
                pumpOnset(item) ?: item.copy(shot = shot).shotStartByClock()
            }
        val start = if (item.syncLocked || item.shotStartInVideoUs != null) null else placed ?: 0L
        edit(push) { current ->
            val with = current.copy(shot = shot)
            if (start != null && with.shotStartInVideoUs == null) with.withShotStart(start) else with
        }
        if (start != null && placed == null) {
            ui.update { it.copy(message = Res.string.edit_no_pump_at_zero.text()) }
        }
    }

    /** Where the pump starts in the video's sound: from the analysis when it is done, else decoded now. */
    private suspend fun pumpOnset(item: VideoItem): Long? =
        ui.value.pumpUs
            ?: withContext(work) {
                val pcm = runCatching { probe.decodeMonoPcm(store.videoPath(item)) }.getOrNull()
                pcm?.let { PumpOnsetDetector().detect(it.samples, it.sampleRate) }
            }

    override fun openSheet(sheet: EditSheet) {
        if (sheet == EditSheet.SHOT) loadShots()
        fetcher?.reset()
        ui.update { it.copy(sheet = sheet, graphPage = graphPage(sheet, it.graphPage), visualizer = null) }
    }

    override fun closeSheet() {
        fetcher?.reset()
        ui.update { it.copy(sheet = null, savedToGallery = false, visualizer = null) }
    }

    override fun openVisualizer() {
        val f = fetcher ?: return
        ui.update { it.copy(visualizer = f.state.value) }
    }

    /** A shot shared on visualizer.coffee, by its code or link, attached as one picked here. */
    override fun fetchFromVisualizer(input: String) {
        fetcher?.fetch(input) { shot ->
            ui.update { it.copy(sheet = null, visualizer = null) }
            attachShot(shot)
        }
    }

    override fun acknowledgeExport() = ui.update { it.copy(exported = false, savedToGallery = false) }

    fun showMessage(text: UiText) = ui.update { it.copy(message = text) }

    override fun clearMessage() = ui.update { it.copy(message = null) }

    override fun pickShot(summary: ShotSummary) {
        ui.update { it.copy(busy = Res.string.edit_loading_shot.text(), sheet = null) }
        viewModelScope.launch {
            val shot = withContext(work) { runCatching { source.loadShot(summary.id) }.getOrNull() }
            if (shot == null) {
                ui.update { it.copy(busy = null, message = Res.string.edit_shot_load_failed.text()) }
                return@launch
            }
            attach(shot)
            ui.update { it.copy(busy = null) }
        }
    }

    /**
     * Attaches a shot file: a Gaggiuino's `.shot`, a visualizer.coffee download or a Beanconqueror brew. The shot in it
     * carries its own clock, so the flag on the timeline still decides where it starts in the video.
     */
    fun importShotFile(path: Path) {
        ui.update { it.copy(busy = Res.string.edit_reading_file.text(), sheet = null) }
        viewModelScope.launch {
            val shot =
                withContext(work) {
                    runCatching { ShotFiles.parse(fs.read(path) { readByteArray() }, fallbackId = "file-$id") }
                        .also { fs.delete(path, mustExist = false) }
                        .getOrNull()
                }
            if (shot == null) {
                ui.update { it.copy(busy = null, message = Res.string.edit_not_a_shot_file.text()) }
                return@launch
            }
            attach(shot)
            ui.update { it.copy(busy = null) }
        }
    }

    /**
     * A shot handed over as JSON (the phones' machine shot picker loads it outside the editor), attached once the video
     * is in. Anything that does not read as a shot is dropped.
     */
    fun attachPickedShot(json: String) {
        viewModelScope.launch {
            ui.first { it.item != null }
            val shot =
                withContext(work) {
                    runCatching { SidecarCodec.json.decodeFromString(Shot.serializer(), json) }.getOrNull()
                } ?: return@launch
            attach(shot)
            ui.update { it.copy(busy = null) }
        }
    }

    /** A whole shot fetched from outside the editor (visualizer.coffee), attached as one picked here. */
    private fun attachShot(shot: Shot) {
        viewModelScope.launch {
            attach(shot)
            ui.update { it.copy(busy = null) }
        }
    }

    /** Finds the pump in the audio and puts the flag there; the user can still move it. */
    override fun findStart() {
        val item = ui.value.item ?: return
        if (item.syncLocked) return
        ui.update { it.copy(busy = Res.string.edit_finding_start.text()) }
        viewModelScope.launch {
            val onset = pumpOnset(item)
            if (onset == null) {
                ui.update { it.copy(busy = null, message = Res.string.edit_no_pump_by_hand.text()) }
            } else {
                edit(push = true) { it.withShotStart(onset) }
                ui.update { it.copy(busy = null) }
            }
        }
    }

    /** Saves now rather than after the debounce, so the export renders exactly what is on screen, then renders it. */
    override fun saveAndExport(quality: Quality, withSound: Boolean, to: ExportManager.Destination) {
        val item = state?.item ?: return
        saveJob?.cancel()
        ui.update {
            it.copy(
                exporting = 0f,
                exportingQuality = quality,
                savedToGallery = false,
                savedVideo = null,
                sheet = EditSheet.EXPORT,
                askExportNotifications = exportsNotify && !settings.value.exportNotificationsAsked,
            )
        }
        viewModelScope.launch {
            withContext(work) { library.save(item) }
            exports.enqueue(listOf(id), quality, withSound, to)
        }
    }

    /** Remembers [quality] for exports [to] that destination, this video's and the next ones'. */
    override fun pickQuality(to: ExportManager.Destination, quality: Quality) {
        if (quality !in ui.value.qualities) return
        settings.update {
            when (to) {
                ExportManager.Destination.GALLERY -> it.copy(galleryQuality = quality)
                ExportManager.Destination.SHARE -> it.copy(shareQuality = quality)
            }
        }
        ui.update { it.withExportQualities() }
    }

    /** Each destination's quality out of what this video allows, from what was picked last. */
    private fun EditUiState.withExportQualities(): EditUiState {
        val picked = settings.value
        return copy(
            galleryQuality = exportQuality(ExportManager.Destination.GALLERY, qualities, picked.galleryQuality),
            shareQuality = exportQuality(ExportManager.Destination.SHARE, qualities, picked.shareQuality),
        )
    }

    /** Stops the render in progress; the screen stays, with nothing exported. */
    override fun cancelExport() = exports.cancel()

    override fun answerExportNotifications(allow: Boolean) {
        settings.update { it.copy(exportNotificationsAsked = true) }
        ui.update { it.copy(askExportNotifications = false) }
    }

    override fun openSavedVideo() {
        ui.value.savedVideo?.let(exports::open)
    }

    /**
     * The filmstrip and the waveform: [THUMBNAILS] frames, each from the middle of its slice so the first is not the
     * black opening frame, and the sound's loudness with the pump start it holds.
     */
    private fun analyse(item: VideoItem) {
        val video = store.videoPath(item)
        viewModelScope.launch {
            val pcm =
                withContext(work) {
                    runCatching { probe.decodeMonoPcm(video, maxSeconds = (item.durationUs / 1_000_000 + 1).toInt()) }
                        .getOrNull()
                }
            if (pcm != null) {
                val (wave, pump) =
                    withContext(work) {
                        peaks(pcm.samples, WAVE_BUCKETS) to PumpOnsetDetector().detect(pcm.samples, pcm.sampleRate)
                    }
                ui.update { it.copy(waveform = wave, pumpUs = pump) }
            }
        }
        viewModelScope.launch {
            val dir = cacheDir / "strip" / item.id
            val frames = mutableListOf<ImageBitmap>()
            ui.update { it.copy(stripLoading = true) }
            for (i in 0 until THUMBNAILS) {
                val atUs = ((i + 0.5) / THUMBNAILS * item.durationUs).toLong()
                val bitmap =
                    withContext(work) {
                        runCatching {
                            fs.createDirectories(dir)
                            val out = dir / "$i.jpg"
                            if (!fs.exists(out)) probe.writeThumbnail(video, atUs, out, maxWidthPx = THUMB_WIDTH_PX)
                            fs.read(out) { readByteArray() }.decodeToImageBitmap()
                        }
                            .getOrNull()
                    }
                if (bitmap == null) {
                    // A frame that cannot be made leaves a hole: no strip, as before.
                    ui.update { it.copy(stripLoading = false) }
                    return@launch
                }
                frames += bitmap
            }
            // All at once: frames trickling in one by one look unfinished.
            ui.update { it.copy(thumbnails = frames.toList(), stripLoading = false) }
        }
    }

    override fun loadShots() {
        if (ui.value.shots.isNotEmpty()) return
        viewModelScope.launch {
            val shots = withContext(work) { runCatching { source.history() }.getOrDefault(emptyList()) }
            ui.update { it.copy(shots = shots) }
        }
    }

    private fun edit(push: Boolean, change: (VideoItem) -> VideoItem) {
        val s = state ?: return
        s.change(push, change)
        publish()
        scheduleSave()
    }

    private fun publish() {
        val s = state ?: return
        val style = styles.resolve(s.item.overlay, s.item.shot?.source)
        ui.update { it.copy(item = s.item, style = style, canUndo = s.canUndo, canRedo = s.canRedo) }
        if (style.adapt.enabled) measureLuma(s.item) else lumaFor = null
    }

    /** The rect the brightness track was measured for; a moved graph needs a new one. */
    private var lumaFor: Triple<app.onlyshots.core.model.NormRect, VideoFormat, Reframe>? = null

    /** The source picture's width over height, once read: where the crop takes the graph's backdrop from. */
    private var sourceAspect: Float? = null
    private var lumaJob: Job? = null

    /**
     * How bright the video is behind the graph over time, for a style that adapts to it: the same track the export
     * measures, so the preview switches where the export will. Measured a moment after the graph stops moving.
     */
    private fun measureLuma(item: VideoItem) {
        val key = Triple(item.overlay.rect, item.format, item.reframe)
        if (lumaFor == key) return
        lumaFor = key
        lumaJob?.cancel()
        lumaJob = viewModelScope.launch {
            delay(LUMA_DELAY_MS)
            val track =
                withContext(work) {
                    runCatching {
                        LumaTrack.measure(
                            sampler,
                            store.videoPath(item),
                            item.overlay.rect,
                            item.trimInUs,
                            item.trimOutUs,
                            item.sourceMapping(
                                sourceAspect ?: probe.info(store.videoPath(item)).aspect?.also { sourceAspect = it }
                            ),
                        )
                    }
                        .getOrNull()
                }
            ui.update { it.copy(luma = track) }
        }
    }

    /** Debounced: a drag writes one sidecar, not one per frame. */
    private fun scheduleSave() {
        val item = state?.item ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            withContext(work) { library.save(item) }
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
        const val THUMBNAILS = 24
        const val LUMA_DELAY_MS = 600L
        const val THUMB_WIDTH_PX = 108
        const val WAVE_BUCKETS = 240

        /** The loudest sample in each of [buckets] even slices, scaled so the loudest slice is 1. */
        fun peaks(samples: FloatArray, buckets: Int): FloatArray {
            if (samples.isEmpty()) return FloatArray(0)
            val out = FloatArray(buckets)
            val per = (samples.size + buckets - 1) / buckets
            for (b in 0 until buckets) {
                var max = 0f
                val end = minOf(samples.size, (b + 1) * per)
                for (i in b * per until end) max = maxOf(max, kotlin.math.abs(samples[i]))
                out[b] = max
            }
            val top = out.maxOrNull()?.takeIf { it > 0f } ?: return out
            for (b in out.indices) out[b] /= top
            return out
        }
    }
}
