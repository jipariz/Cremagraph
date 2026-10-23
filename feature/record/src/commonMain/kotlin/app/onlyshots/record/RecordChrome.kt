package app.onlyshots.record

import androidx.compose.ui.geometry.Rect
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.component.RecordButtonState
import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Series
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.resources.UiText
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.WindowLayout

/*
 * The record screen's rules, as plain functions: what shows, where the controls go and what they say. The layouts read
 * these decided values and only place things.
 */

/** What the camera pane draws besides the picture, decided by the layout around it. */
internal enum class PaneChrome {
    /** Full screen: the floating controls over it have the lenses and the status. */
    Floating,

    /**
     * A pane of its own (a tabletop's upper half, a flip's in flex mode): its lenses and status chip on the picture.
     */
    Own,

    /**
     * A cover screen or a sliver of split screen: room for the picture and nothing else. The chip would wrap to six
     * lines and the graph card be smaller than its own labels.
     */
    Bare,
}

/** Where the floating controls go over a full-screen camera. */
internal enum class ControlsPlacement {
    /** An open book foldable: never across the fold, the top row and rules on the left page, the key on the right. */
    Fold,

    /** On its side: the lenses, the key and "simulate shot" down the trailing edge, the rules at the bottom. */
    Landscape,

    /** A tablet held upright: the lenses, the key and the rules float at the bottom, centred. */
    TabletUpright,

    /** A phone held upright: the lenses over the key at the foot; status and rules out of the graph's way. */
    PhoneUpright,
}

/** Where the floating controls go in this window, the camera filming [capture]'s way. */
internal fun WindowLayout.controlsPlacement(capture: VideoFormat): ControlsPlacement =
    when {
        onBookFold -> ControlsPlacement.Fold
        capture.landscape -> ControlsPlacement.Landscape
        kind == LayoutKind.Wide -> ControlsPlacement.TabletUpright
        else -> ControlsPlacement.PhoneUpright
    }

/** An open book foldable, its pages side by side: the record screen's controls and sheets never cross the fold. */
internal val WindowLayout.onBookFold: Boolean
    get() = fold && kind == LayoutKind.Wide

/** Where the right page starts on an open book foldable, in dp: the sheets fill that page. Null elsewhere. */
internal val WindowLayout.foldPageStartDp: Float?
    get() = if (onBookFold) splitDp + hingeDp else null

/** Recording, or no data to draw: the graph is put down and its sheets and the settings close. */
internal val RecordUiState.endsGraphEditing: Boolean
    get() = recording || !hasData

/**
 * Whether the graph is drawn: without a machine there is nothing to draw (the screen is a camera, and the shot comes in
 * the editor); without a camera neither, there is no picture to draw it over.
 */
internal fun RecordUiState.showsGraph(granted: Boolean, chrome: PaneChrome): Boolean =
    chrome != PaneChrome.Bare && hasData && granted

/** Whether the graph can be placed by hand: before a shot; while recording nothing on the picture moves. */
internal fun RecordUiState.editsGraph(granted: Boolean, chrome: PaneChrome): Boolean =
    showsGraph(granted, chrome) && !recording

/** Whether "focus locked" shows under the ring: a held focus, a ring to sit under, and not mid-take. */
internal fun RecordUiState.showsFocusLock(
    camera: CameraState,
    ringShown: Boolean,
    granted: Boolean,
    chrome: PaneChrome,
): Boolean = camera.focusLocked && ringShown && granted && chrome != PaneChrome.Bare && !recording

/** Whether the lenses are offered: with a picture, and not mid-take, where they slide away. */
internal fun RecordUiState.showsLenses(granted: Boolean): Boolean = !recording && granted

/** The graph is in the upper half: the status chip goes over the bottom controls, out of its way. */
internal fun graphOnTop(rect: NormRect): Boolean = rect.y + rect.h / 2f < 0.5f

/** Where a pane's own status chip stands, as a vertical bias: the corner opposite the graph (1 bottom, -1 top). */
internal fun statusChipBias(rect: NormRect): Float = if (rect.y < 0.5f) 1f else -1f

/**
 * The graph as drawn live at [rect]: before the first sample it shows every selected reading, waiting, but only those
 * this source can send (a scale has no pressure), as the data sheet offers them.
 */
internal fun liveOverlay(overlay: OverlaySettings, source: SourceKind, rect: NormRect): OverlaySettings =
    overlay.forSource(source).let { o ->
        val measured = Capabilities.of(source, hasScale = true).series
        o.copy(rect = rect, series = o.series.filterTo(mutableSetOf()) { it == Series.TARGETS || it in measured })
    }

/** The live graph's title: the profile ([fallback] while unnamed) and the machine. */
internal fun graphTitle(profileName: String, fallback: String, machine: String): String =
    "${profileName.ifEmpty { fallback }} · $machine"

/**
 * Whether the status chip says anything the screen does not already: without a machine it would only say so again,
 * beside the source's chip and the hint over the button.
 */
internal val RecordUiState.statusWorthShowing: Boolean
    get() = hasData || recording || noCamera

/** The record button's look for the session's phase. */
internal fun RecordUiState.buttonState(): RecordButtonState =
    when {
        recording -> RecordButtonState.Recording
        noCamera -> RecordButtonState.Disabled
        phase == RecordPhase.ARMED -> RecordButtonState.Armed
        else -> RecordButtonState.Idle
    }

/** What the rules block shows: done or waiting while it runs, the rules themselves before, a hint without data. */
internal enum class RulesMode {
    RUNNING,
    RULES,
    HINT,
}

/** The rules block's content, or null without a camera: there is no take to rule, the bar holds only the key. */
internal fun RecordUiState.rulesMode(): RulesMode? =
    when {
        recording -> RulesMode.RUNNING
        noCamera -> null
        hasData -> RulesMode.RULES
        else -> RulesMode.HINT
    }

/** What the panels around the camera share, so each posture's panel is a few lines of layout. */
internal class TakeInputs(
    val state: RecordUiState,
    val camera: CameraState,
    val settings: RecordingSettings,
    /** The source's newest reading, read only where readings are shown: it changes with every sample. */
    val live: () -> Sample?,
    val actions: RecordActions,
    val canSimulateShot: Boolean,
    val saveError: UiText?,
    val onBack: () -> Unit,
    val onSettings: () -> Unit,
) {
    /** "simulate shot" can pull the replayed shot: armed, with the camera. */
    val canSimulate: Boolean
        get() = canSimulateShot && state.phase == RecordPhase.ARMED && !state.noCamera

    /** The newest reading: the shot's while recording, the source's before. */
    val sample: Sample?
        get() = if (state.recording) state.shot.samples.lastOrNull() ?: live() else live()
}

/** The floating controls' parts whose bounds decide what of the window they leave free. */
internal enum class ControlSlot {
    Window,
    Top,
    Bottom,
    Side,
    /** The right page on a fold: the graph is kept on that page. */
    Page,
}

/**
 * Where the floating controls are, in root px, and what of the window they last left free. Kept out of snapshot state:
 * they move on every layout pass (a rules block resizing), and only what they leave free is passed on, when it changes.
 */
internal class ControlRects {
    private val rects = mutableMapOf<ControlSlot, Rect>()
    private var reported: Rect? = null

    operator fun set(slot: ControlSlot, rect: Rect) {
        rects[slot] = rect
    }

    private operator fun get(slot: ControlSlot): Rect = rects[slot] ?: Rect.Zero

    /** What of the window the controls leave free for [placement]; null before the window is measured. */
    fun free(placement: ControlsPlacement): Rect? {
        val window = this[ControlSlot.Window]
        val page = this[ControlSlot.Page]
        val bottom = this[ControlSlot.Bottom]
        val side = this[ControlSlot.Side]
        return when {
            window.isEmpty -> null
            placement == ControlsPlacement.Fold && !page.isEmpty ->
                Rect(page.left, page.top, page.right, if (bottom.isEmpty) page.bottom else bottom.top)
            else ->
                Rect(
                    window.left,
                    this[ControlSlot.Top].bottom.coerceAtLeast(window.top),
                    if (placement == ControlsPlacement.Landscape && !side.isEmpty) side.left else window.right,
                    if (bottom.isEmpty) window.bottom else bottom.top,
                )
        }
    }

    /** What is free now, if it changed since it was last reported; it then counts as reported. */
    fun changed(placement: ControlsPlacement): Rect? =
        free(placement)?.takeIf { it != reported }?.also { reported = it }
}
