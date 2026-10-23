package app.onlyshots.record

import app.onlyshots.camera.CameraController
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopReason
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.model.StopRules
import app.onlyshots.core.model.formatClock
import app.onlyshots.core.model.oneDecimal
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.record_default_title
import app.onlyshots.core.resources.record_start_before
import app.onlyshots.core.resources.record_start_by_hand
import app.onlyshots.core.resources.record_start_no_pre_roll
import app.onlyshots.core.resources.record_start_with_shot
import app.onlyshots.core.resources.record_status_armed
import app.onlyshots.core.resources.record_status_brewing
import app.onlyshots.core.resources.record_status_busy
import app.onlyshots.core.resources.record_status_connecting
import app.onlyshots.core.resources.record_status_manual
import app.onlyshots.core.resources.record_status_no_camera
import app.onlyshots.core.resources.record_status_no_machine
import app.onlyshots.core.resources.record_status_pre_roll
import app.onlyshots.core.resources.record_status_recording
import app.onlyshots.core.resources.record_status_saved
import app.onlyshots.core.resources.record_status_shot_ended
import app.onlyshots.core.resources.record_status_waiting
import app.onlyshots.core.resources.record_stop_fixed
import app.onlyshots.core.resources.record_stop_manual
import app.onlyshots.core.resources.record_stop_short_fixed
import app.onlyshots.core.resources.record_stop_short_manual
import app.onlyshots.core.resources.record_stop_short_tail
import app.onlyshots.core.resources.record_stop_short_weight
import app.onlyshots.core.resources.record_stop_tail
import app.onlyshots.core.resources.record_stop_weight
import app.onlyshots.core.resources.record_trigger_decent
import app.onlyshots.core.resources.record_trigger_gaggimate
import app.onlyshots.core.resources.record_trigger_gaggiuino
import app.onlyshots.core.resources.record_trigger_scale
import app.onlyshots.core.resources.text
import app.onlyshots.sources.BrewEvent
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.LiveShotAssembler
import app.onlyshots.sources.ShotSource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RecordPhase {
    ARMED,
    MANUAL,
    NOT_REACHABLE,
    RECORDING,
    SAVED,
}

/** A finished recording, ready to be written to the library. */
data class FinishedRecording(
    val shot: Shot?,
    val durationUs: Long,
    val shotStartInVideoUs: Long?,
    val stopReason: StopReason?,
    /**
     * Where the take was started in the file, on the same clock as [shotStartInVideoUs]: the shot's own start when it
     * started the take, earlier when the take was started by hand and the shot came after.
     */
    val takeStartInVideoUs: Long = shotStartInVideoUs ?: 0L,
)

data class RecordUiState(
    val phase: RecordPhase,
    val status: UiText,
    val connection: ConnectionState,
    val sourceKind: SourceKind,
    val shot: Shot,
    /** Position in the file being recorded; drives the live overlay. */
    val videoTimeUs: Long = 0L,
    val shotStartInVideoUs: Long = 0L,
    val stopRuleText: UiText,
    val finished: FinishedRecording? = null,
    /** The camera was refused: nothing can be filmed, so the record button is off and a brew starts nothing. */
    val noCamera: Boolean = false,
) {
    /** Settings sheets are unavailable while recording. */
    val settingsLocked: Boolean
        get() = phase == RecordPhase.RECORDING

    val recording: Boolean
        get() = phase == RecordPhase.RECORDING

    /**
     * Whether there is live data to draw. Without a connected machine the record screen is a plain camera: no graph, no
     * auto-start, and the shot is added in the editor. A machine dropping out mid-shot keeps what was filming.
     */
    val hasData: Boolean
        get() = connection is ConnectionState.Connected || (recording && shot.samples.isNotEmpty())
}

/**
 * The Record screen's logic: armed → recording → saved, auto-start from the source's trigger, the stop rule and safety
 * stops evaluated every [tickMs] on source time.
 *
 * [nowUs] is a monotonic clock in µs. Without a camera the recording position is derived from it; with one, the
 * camera's trigger presentation time replaces [RecordingSettings.preRollSeconds].
 */
class RecordSession(
    private val scope: CoroutineScope,
    private val source: ShotSource,
    private val settings: RecordingSettings,
    private val nowUs: () -> Long,
    private val camera: CameraController? = null,
    private val freeBytes: () -> Long = camera?.let { { it.freeBytes() } } ?: { Long.MAX_VALUE },
    private val wallClock: () -> Instant = { Clock.System.now() },
    private val tickMs: Long = 100,
) {
    val state: StateFlow<RecordUiState>
        field =
            MutableStateFlow(
                RecordUiState(
                        phase = idlePhase(source.connection.value),
                        status = "".raw(),
                        connection = source.connection.value,
                        sourceKind = source.kind,
                        shot = emptyShot(),
                        stopRuleText = stopRuleText(settings.stopRule),
                    )
                    .withStatus()
            )

    /** The camera may not be used; see [RecordUiState.noCamera]. */
    var noCamera: Boolean = false
        set(value) {
            field = value
            state.update { it.copy(noCamera = value).withStatus() }
        }

    private var assembler: LiveShotAssembler? = null
    private var fileStartUs = 0L

    /** Where the take was started in the file: see [FinishedRecording.takeStartInVideoUs]. */
    private var takeStartUs = 0L
    /** source clock − our clock, known once the brew started. */
    private var sourceOffsetUs: Long? = null
    private var tickJob: Job? = null

    init {
        scope.launch {
            source.connection.collect { c ->
                assembler?.let { if (c !is ConnectionState.Connected) it.onConnectionLost() }
                state.update { s ->
                    val phase =
                        if (s.phase == RecordPhase.RECORDING || s.phase == RecordPhase.SAVED) s.phase else idlePhase(c)
                    s.copy(connection = c, phase = phase).withStatus()
                }
            }
        }
        // The assembler is not thread-safe and these two collectors both feed it: this scope is
        // the view model's, which is the main dispatcher.
        scope.launch { source.brewEvents.collect { e -> onBrewEvent(e) } }
        scope.launch {
            source.live.collect { sample ->
                val a = assembler ?: return@collect
                a.onSample(sample)
            }
        }
    }

    /** The record / stop button. */
    fun recordPressed() {
        when (state.value.phase) {
            RecordPhase.RECORDING -> stop(null)
            RecordPhase.SAVED -> recordAnother()
            else -> if (!noCamera) startRecording(trigger = null)
        }
    }

    fun recordAnother() {
        state.update {
            it.copy(
                    phase = idlePhase(it.connection),
                    finished = null,
                    shot = emptyShot(),
                    videoTimeUs = 0,
                    shotStartInVideoUs = 0,
                )
                .withStatus()
        }
    }

    private fun onBrewEvent(e: BrewEvent) {
        val s = state.value
        when {
            e is BrewEvent.Started && s.phase == RecordPhase.ARMED && settings.autoStart && !noCamera ->
                startRecording(e)
            s.phase == RecordPhase.RECORDING -> {
                val a = assembler ?: return
                if (e is BrewEvent.Started && a.startSourceUs == null) {
                    // Recording was started by hand; the shot starts now.
                    sourceOffsetUs = e.atUs - nowUs()
                    state.update { it.copy(shotStartInVideoUs = nowUs() - fileStartUs) }
                }
                a.onBrewEvent(e)
            }
        }
    }

    private fun startRecording(trigger: BrewEvent.Started?) {
        val now = nowUs()
        // The camera has been recording since the app armed, so it knows how much picture is
        // already in the file. Without one, fall back to the nominal pre-roll.
        val preRollUs = camera?.trigger() ?: if (trigger != null) settings.preRollSeconds * 1_000_000L else 0L
        fileStartUs = now - preRollUs
        takeStartUs = preRollUs
        val a =
            LiveShotAssembler(
                id = "rec-${wallClock().toEpochMilliseconds()}",
                source = source.kind,
                profileName = UNNAMED,
                startedAt = wallClock(),
            )
        assembler = a
        sourceOffsetUs = null
        if (trigger != null) {
            a.onBrewEvent(trigger)
            sourceOffsetUs = trigger.atUs - now
        }
        state.update {
            it.copy(
                    phase = RecordPhase.RECORDING,
                    shot = a.shot(),
                    videoTimeUs = preRollUs,
                    shotStartInVideoUs = if (trigger != null) preRollUs else 0L,
                    finished = null,
                )
                .withStatus()
        }
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(tickMs)
                tick()
            }
        }
    }

    private fun tick() {
        val a = assembler ?: return
        val now = nowUs()
        sourceOffsetUs?.let { a.onTick(now + it) }
        val videoUs = now - fileStartUs
        val reason =
            StopRules.evaluate(
                settings.stopRule,
                a.progress(),
                recordingElapsedUs = videoUs,
                freeBytes = freeBytes(),
                // Recordings are always at the camera's best, so a 4K camera needs the 4K margin.
                minFreeBytes =
                    if (camera?.state?.value?.supports4k == true) StopRules.MIN_FREE_BYTES_4K
                    else StopRules.MIN_FREE_BYTES,
            )
        state.update { it.copy(shot = a.shot(), videoTimeUs = videoUs).withStatus() }
        if (reason != null) stop(reason)
    }

    private fun stop(reason: StopReason?) {
        val a = assembler ?: return
        tickJob?.cancel()
        tickJob = null
        val durationUs = nowUs() - fileStartUs
        val shot = a.shot().takeIf { a.startSourceUs != null }
        assembler = null
        state.update {
            it.copy(
                    phase = RecordPhase.SAVED,
                    videoTimeUs = durationUs,
                    shot = shot ?: it.shot,
                    finished =
                        FinishedRecording(
                            shot,
                            durationUs,
                            if (shot != null) it.shotStartInVideoUs else null,
                            reason,
                            takeStartInVideoUs = takeStartUs,
                        ),
                )
                .withStatus()
        }
    }

    private fun idlePhase(c: ConnectionState): RecordPhase =
        when {
            c !is ConnectionState.Connected -> RecordPhase.NOT_REACHABLE
            settings.autoStart -> RecordPhase.ARMED
            else -> RecordPhase.MANUAL
        }

    private fun emptyShot() =
        Shot(
            id = "live",
            source = source.kind,
            profileName = UNNAMED,
            startedAt = wallClock(),
            durationUs = 0,
            finalWeight = null,
            samples = emptyList(),
            phaseStartsUs = emptyList(),
        )

    private fun RecordUiState.withStatus(): RecordUiState = copy(status = statusText(this))

    private fun statusText(s: RecordUiState): UiText =
        if (s.noCamera && !s.recording) Res.string.record_status_no_camera.text()
        else
            when (s.phase) {
                // One clock at a time: the brew while the shot runs, else how long the take has run since it was
                // started (the pre-roll filmed before that is not counted).
                RecordPhase.RECORDING -> {
                    val shotT = s.videoTimeUs - s.shotStartInVideoUs
                    val ended = assembler?.endedAtUs
                    val take = formatClock((s.videoTimeUs - takeStartUs).coerceAtLeast(0L))
                    when {
                        assembler?.startSourceUs == null && s.connection is ConnectionState.Connected ->
                            Res.string.record_status_waiting.text(take)
                        assembler?.startSourceUs == null -> Res.string.record_status_recording.text(take)
                        shotT < 0 -> Res.string.record_status_pre_roll.text()
                        ended == null -> Res.string.record_status_brewing.text((shotT / 1_000_000f).oneDecimal())
                        else -> Res.string.record_status_shot_ended.text(s.stopRuleText)
                    }
                }
                RecordPhase.ARMED -> Res.string.record_status_armed.text(triggerText(source.kind))
                RecordPhase.MANUAL -> Res.string.record_status_manual.text()
                RecordPhase.NOT_REACHABLE ->
                    when (s.connection) {
                        ConnectionState.Busy -> Res.string.record_status_busy.text()
                        ConnectionState.Connecting -> Res.string.record_status_connecting.text()
                        else -> Res.string.record_status_no_machine.text()
                    }
                RecordPhase.SAVED -> Res.string.record_status_saved.text()
            }

    companion object {
        /**
         * The live shot's profile name until the recording is saved, where the view model puts the [defaultProfileName]
         * in words: the session runs outside composition and cannot resolve one.
         */
        const val UNNAMED = ""

        fun triggerText(kind: SourceKind): UiText =
            when (kind) {
                SourceKind.GAGGIUINO -> Res.string.record_trigger_gaggiuino.text()
                SourceKind.GAGGIMATE -> Res.string.record_trigger_gaggimate.text()
                SourceKind.DECENT -> Res.string.record_trigger_decent.text()
                SourceKind.SCALE -> Res.string.record_trigger_scale.text()
                // Never live: a fetched shot is not recorded with.
                SourceKind.VISUALIZER -> Res.string.record_start_by_hand.text()
            }

        /** How a recording starts, in a few words for the rules tile: a title and an optional second line. */
        fun startRuleShort(settings: RecordingSettings): Pair<UiText, UiText?> =
            if (!settings.autoStart) Res.string.record_start_by_hand.text() to null
            else
                Res.string.record_start_with_shot.text() to
                    if (settings.preRollSeconds == 0) Res.string.record_start_no_pre_roll.text()
                    else Res.string.record_start_before.text(settings.preRollSeconds.toString())

        /** How a recording stops, in a few words for the rules tile. */
        fun stopRuleShort(rule: StopRule): UiText =
            when (rule) {
                is StopRule.ShotEndPlusTail -> Res.string.record_stop_short_tail.text(rule.tailSeconds.toString())
                is StopRule.WeightSettles -> Res.string.record_stop_short_weight.text()
                is StopRule.Fixed -> Res.string.record_stop_short_fixed.text(rule.seconds.toString())
                StopRule.Manual -> Res.string.record_stop_short_manual.text()
            }

        fun stopRuleText(rule: StopRule): UiText =
            when (rule) {
                is StopRule.ShotEndPlusTail -> Res.string.record_stop_tail.text(rule.tailSeconds.toString())
                is StopRule.WeightSettles -> Res.string.record_stop_weight.text(rule.seconds.toString())
                is StopRule.Fixed -> Res.string.record_stop_fixed.text(rule.seconds.toString())
                StopRule.Manual -> Res.string.record_stop_manual.text()
            }

        /** The title a shot gets when the machine names none: the brand for a scale's, else "Shot". */
        fun defaultProfileName(kind: SourceKind): UiText =
            when (kind) {
                SourceKind.SCALE -> "OnlyShots".raw()
                else -> Res.string.record_default_title.text()
            }
    }
}
