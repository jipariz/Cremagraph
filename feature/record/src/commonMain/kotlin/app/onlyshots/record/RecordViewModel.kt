package app.onlyshots.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.onlyshots.camera.CameraController
import app.onlyshots.camera.CameraState
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.record_save_failed
import app.onlyshots.core.resources.resolve
import app.onlyshots.core.resources.text
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.SourceHolder
import app.onlyshots.sources.fake.FakeShotSource
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.Library
import app.onlyshots.storage.LibraryStore
import app.onlyshots.style.StyleLibrary
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModel(
    private val source: SourceHolder,
    private val settingsRepo: AppSettingsRepo,
    val camera: CameraController,
    private val store: LibraryStore,
    private val library: Library,
    val styles: StyleLibrary,
) : ViewModel(), RecordActions {
    private val start = TimeSource.Monotonic.markNow()

    /** Whether the record screen is in front: a scale that connects then is tared too. */
    private var onScreen = false

    /** The session bakes its machine and its settings in, so changing either builds a new one. */
    private var sessionScope: CoroutineScope? = null
    private val _session = MutableStateFlow(newSession())

    val state: StateFlow<RecordUiState> =
        _session.flatMapLatest { it.state }.stateIn(viewModelScope, SharingStarted.Eagerly, _session.value.state.value)

    val cameraState: StateFlow<CameraState> = camera.state

    /** The graph while it is being dragged: shown at once, written to the settings file once the gesture settles. */
    private val overlayDraft = MutableStateFlow<OverlaySettings?>(null)
    private var overlaySave: Job? = null

    /**
     * The shape this take records in when it is not the saved one: 16:9 for a tall format while a book foldable rests
     * on its side ([onItsSide]), until "keep 9:16" ([keepFormat]). The saved setting is left alone.
     */
    val takeFormat: StateFlow<VideoFormat?>
        field = MutableStateFlow<VideoFormat?>(null)

    /** "keep 9:16" was pressed while on its side; forgotten when it stands up again. */
    private var keepTall = false

    /** The format chosen in the settings, whatever this take does. */
    val savedFormat: StateFlow<VideoFormat> =
        settingsRepo.state
            .map { it.recording.format }
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepo.value.recording.format)

    /** The settings the next take records with: the saved ones, the graph being dragged, the take's own format. */
    val settings: StateFlow<RecordingSettings> =
        combine(settingsRepo.state.map { it.recording }, overlayDraft, takeFormat) { recording, draft, take ->
                val base = if (draft == null) recording else recording.copy(overlay = draft)
                if (take == null) base else base.withFormat(take)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepo.value.recording)

    /** The newest reading from the source, for the tiles before a take starts. */
    val live: StateFlow<Sample?> =
        source.live.map<Sample, Sample?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The id of each recording once it is in the library, so the screen can open it in the editor. */
    val saved: SharedFlow<String>
        field = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Why the last recording could not be kept, until the next one is. */
    val saveError: StateFlow<UiText?>
        field = MutableStateFlow<UiText?>(null)

    /**
     * The replayed Zer0 shot is in use (a debug build, or "sample shot" picked on Home): a shot can be pulled by hand.
     */
    val canSimulateShot: Boolean
        get() = source.current.value.let { it is FakeShotSource && it.connection.value == ConnectionState.Connected }

    init {
        viewModelScope.launch {
            _session
                .flatMapLatest { it.state }
                .map { it.finished }
                .filterNotNull()
                .distinctUntilChanged()
                .collect(::save)
        }
        viewModelScope.launch {
            // A scale that connects while the record screen is in front is tared as if the screen had just opened.
            source.connection
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected -> if (connected && onScreen) prepareSource() }
        }
        viewModelScope.launch {
            // A machine or a settings change restarts the session, but never mid-recording. The graph is not one of
            // them: it is only read when the recording is saved, and moving it must not re-arm the camera.
            combine(source.current.map { it.kind }, settings) { kind, recording ->
                    kind to recording.copy(overlay = OverlaySettings())
                }
                .distinctUntilChanged()
                .collect { if (!state.value.recording) _session.value = newSession() }
        }
    }

    override fun recordPressed() = _session.value.recordPressed()

    /** The style the record screen draws with: the user's styles and their defaults per source included. */
    fun style(overlay: OverlaySettings, kind: app.onlyshots.core.model.SourceKind) = styles.resolve(overlay, kind)

    /** Switches the graph to style [id] for the next recordings. */
    override fun pickStyle(id: String) = updateOverlay { styles.pick(it, id) }

    override fun renameStyle(id: String, name: String) = styles.rename(id, name)

    override fun deleteStyle(id: String) = styles.delete(id)

    private fun pinStyle(overlay: OverlaySettings, kind: app.onlyshots.core.model.SourceKind): OverlaySettings {
        val style = styles.resolve(overlay, kind)
        return if (style.builtIn) overlay else overlay.copy(styleId = style.id, style = style)
    }

    /**
     * Always the camera's best quality, and always with sound when the microphone is allowed: muting is the editor's
     * choice, made once the shot is in.
     */
    fun arm(micGranted: Boolean) {
        onScreen = true
        if (wrongWay) return
        prepareSource()
        viewModelScope.launch {
            camera.setMic(micGranted)
            camera.setFourK(camera.state.value.supports4k)
            camera.setFormat(settings.value.format)
            camera.arm(settings.value.preRollSeconds)
        }
    }

    /**
     * The format changed: the pre-roll being kept was filmed the old way, so it starts again in the new one, once the
     * screen has turned (the camera records the way the screen stands). Never under a recording.
     */
    fun rearm(micGranted: Boolean) {
        if (state.value.recording) return
        disarm()
        rearming?.cancel()
        rearming = viewModelScope.launch {
            delay(TURN_MS)
            if (!state.value.recording && onScreen) arm(micGranted)
        }
    }

    private var rearming: Job? = null

    /** The screen stands the other way from the format; see [RecordSession.heldWrongWay]. */
    private var wrongWay = false

    /** The camera was refused; see [RecordUiState.noCamera]. */
    private var noCamera = false

    /** The answer about the camera, from the prompt or from a check on coming back from the system settings. */
    fun cameraAccess(granted: Boolean) {
        noCamera = !granted
        _session.value.noCamera = noCamera
    }

    /**
     * Where the screen cannot be held the format's way (a foldable's inner display ignores the lock), the camera waits
     * until it is turned: it stops filming the pre-roll the wrong way, and arms again once the screen stands right. A
     * recording under way is never cut.
     */
    fun heldWrongWay(wrong: Boolean, micGranted: Boolean) {
        if (wrong == wrongWay) return
        wrongWay = wrong
        _session.value.heldWrongWay = wrong
        if (state.value.recording) return
        rearming?.cancel()
        if (wrong) disarm() else if (onScreen) arm(micGranted)
    }

    fun disarm() = camera.disarm()

    /** Leaving the app saves what is recorded rather than losing it. */
    fun backgrounded() {
        onScreen = false
        if (state.value.recording) recordPressed() else disarm()
    }

    /**
     * Ready the source for a shot (a scale tares), never under a recording. A scale that is not there yet is no error.
     */
    private fun prepareSource() {
        if (state.value.recording) return
        viewModelScope.launch { runCatching { source.prepareForShot() } }
    }

    override fun setZoom(zoom: Float) = camera.setZoom(zoom)

    override fun updateSettings(change: (RecordingSettings) -> RecordingSettings) {
        val before = settingsRepo.value.recording.format
        settingsRepo.update { it.copy(recording = change(it.recording)) }
        // A format picked by hand is the one to use, on its side or not.
        if (settingsRepo.value.recording.format != before) {
            keepTall = true
            takeFormat.value = null
        }
    }

    /**
     * The posture changed: on its side a tall format records 16:9 for the take (the camera fills the upper half), back
     * up the saved format returns. Never under a recording.
     */
    fun onItsSide(side: Boolean) {
        if (state.value.recording) return
        if (!side) keepTall = false
        takeFormat.value = takeFormatFor(side, settingsRepo.value.recording.format, keepTall)
    }

    override fun keepFormat() {
        keepTall = true
        takeFormat.value = null
    }

    /**
     * The graph for the next recordings: style, data, layout, place and size. A drag calls this every frame, so the
     * change is drawn at once and saved after a moment without one.
     */
    override fun updateOverlay(change: (OverlaySettings) -> OverlaySettings) {
        // Moved in the take's own shape, the graph is kept where it lands in the saved one.
        val changed = change(settings.value.overlay)
        val saved = settingsRepo.value.recording.format
        overlayDraft.value =
            takeFormat.value?.let { take -> changed.copy(rect = changed.rect.refit(take, saved)) } ?: changed
        overlaySave?.cancel()
        overlaySave = viewModelScope.launch {
            delay(OVERLAY_SAVE_DELAY_MS)
            val overlay = overlayDraft.value ?: return@launch
            settingsRepo.update { it.copy(recording = it.recording.copy(overlay = overlay)) }
            overlayDraft.value = null
        }
    }

    override fun focusAt(x: Float, y: Float) = camera.focusAt(x, y)

    override fun unlockFocus() = camera.unlockFocus()

    override fun simulateShot() {
        val fake = source.current.value as? FakeShotSource ?: return
        viewModelScope.launch { fake.pull() }
    }

    /**
     * Pulls the sample shot once the camera is armed and the replay is connected, after a moment of pre-roll so the
     * take opens on the cup before the pump starts. Does nothing for any other source.
     */
    suspend fun simulateShotWhenArmed() {
        combine(state, source.connection) { s, c -> s.phase == RecordPhase.ARMED && c == ConnectionState.Connected }
            .first { it }
        delay(SAMPLE_PRE_ROLL_MS)
        if (state.value.phase != RecordPhase.ARMED) return
        // With "start with the shot" off nothing would start the take, so the sample starts it by hand.
        if (!settings.value.autoStart) recordPressed()
        simulateShot()
    }

    private fun newSession(): RecordSession {
        sessionScope?.cancel()
        val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob())
        sessionScope = scope
        return RecordSession(
                scope = scope,
                source = source,
                // A rule this source cannot fire runs as the one that does the same job (a scale: weight settles).
                settings = settingsRepo.value.recording.effectiveFor(source.current.value.kind),
                nowUs = { start.elapsedNow().inWholeMicroseconds },
                camera = camera,
            )
            .also {
                it.heldWrongWay = wrongWay
                it.noCamera = noCamera
            }
    }

    private suspend fun save(take: FinishedRecording) {
        val recording = runCatching { camera.stop() }.getOrNull() ?: return
        // The session leaves the shot unnamed (it cannot put words to it); the default title goes in here.
        val finished =
            take.shot
                ?.takeIf { it.profileName == RecordSession.UNNAMED }
                ?.let { shot ->
                    take.copy(shot = shot.copy(profileName = RecordSession.defaultProfileName(shot.source).resolve()))
                } ?: take
        val settings = settings.value
        // A recording stopped as the app went to the background can come back without a file; that is a lost take to
        // report, not a reason to bring the app down.
        val id = runCatching {
            withContext(Dispatchers.Default) {
                val (id, file) = store.importMove(recording.file.toPath())
                library.save(
                    recordedVideoItem(
                            id = id,
                            file = file,
                            createdAt = Clock.System.now(),
                            recording = recording,
                            finished = finished,
                            preRollSeconds = settings.preRollSeconds,
                            // The style the recording was made with is pinned to it: a user style travels as a copy.
                            overlay =
                                (finished.shot?.source ?: source.kind).let { kind ->
                                    pinStyle(settings.overlay.forSource(kind), kind)
                                },
                        )
                        // A portrait take for 4:5 or 1:1 keeps its middle; the editor can move the crop.
                        .copy(format = settings.format)
                )
                id
            }
        }
            .getOrElse {
                saveError.value = Res.string.record_save_failed.text()
                return
            }
        saveError.value = null
        saved.emit(id)
    }

    override fun onCleared() {
        sessionScope?.cancel()
        sessionScope = null
    }
}

/** A pause this long after the last drag frame counts as the gesture settled. */
private const val OVERLAY_SAVE_DELAY_MS = 400L

/** How long a screen takes to turn before the camera is armed the new way. */
private const val TURN_MS = 700L

/** How long the sample shot waits once armed, so the take opens on the cup before the pump starts. */
private const val SAMPLE_PRE_ROLL_MS = 1_500L
