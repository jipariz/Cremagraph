package app.onlyshots.open

import androidx.compose.runtime.Immutable
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.UiText
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.WindowLayout

/*
 * The Open screen as plain state, and the decisions its layouts read: which page a host gets, how the desk page fits
 * its width. Pure, so the rules are tested without drawing anything.
 */

/**
 * The shot picked for the video: the shot itself, what to call it (its file's name, or its profile), and its origin.
 */
data class ChosenShot(val shot: Shot, val label: String, val from: ShotOrigin)

/** Where a picked shot came from: a `.shot` or `.json` file, or the machine itself (which the shot names). */
enum class ShotOrigin {
    File,
    Machine,
}

/** What the desktop or the web says about itself on its start screen. */
data class OpenHost(
    /** In the header, beside the shield: where it runs. */
    val headerNote: String,
    /** The "videos" column: what comes out, and where. */
    val videosNote: String,
)

/**
 * Who shows the Open screen. The phone app comes to it from its start screen and has a way back ([PhoneApp]); the
 * desktop and the web start on it, saying what they are ([Desk]).
 */
sealed interface OpenEntry {
    data class PhoneApp(val onBack: () -> Unit) : OpenEntry

    data class Desk(
        /** What the desktop or the web says about itself. */
        val host: OpenHost? = null,
        /** The phone app's store links, which the web offers. */
        val stores: StoreLinks? = null,
        /** The app's own privacy screen, for the header's "privacy"; without it the link opens the web page. */
        val onPrivacy: (() -> Unit)? = null,
    ) : OpenEntry
}

/**
 * Everything the Open screen shows: the video picked (and its file's name, size and frame), the shot picked, and how
 * the last pick went. [dragging] while a file is held over the window; [fromMachine]: the machine is one way to the
 * shot (phones, desktop), not only a file (web).
 */
@Immutable
data class OpenUi(
    val picked: VideoItem? = null,
    val pickedFile: PickedFile? = null,
    val shot: ChosenShot? = null,
    val status: UiText? = null,
    val dragging: Boolean = false,
    val fromMachine: Boolean = true,
) {
    /** Both picked: "open editor" can go. */
    val ready: Boolean
        get() = picked != null && shot != null

    /** "opening…" while a picked video is copied in, which the video step says in its place. */
    val opening: UiText?
        get() = status?.takeIf { it == Opening }

    /** Why the last pick did not take, said under the steps. */
    val problem: UiText?
        get() = status?.takeIf { it != Opening }

    /** The video is the step to do next. */
    val videoNext: Boolean
        get() = picked == null

    /** The shot is the step to do next: the video is picked, the shot not yet. */
    val shotNext: Boolean
        get() = picked != null && shot == null
}

/**
 * What the Open screen's body can ask for. [OpenScreen] does it for real; a screenshot preview passes [None], so the
 * whole screen renders from plain state.
 */
interface OpenActions {
    fun pickVideo()

    /** The shot: a file picker at once, or the sheet offering a file or the machine. */
    fun pickShot()

    fun openEditor()

    object None : OpenActions {
        override fun pickVideo() = Unit

        override fun pickShot() = Unit

        override fun openEditor() = Unit
    }
}

/**
 * The store links when the desk page is in a phone's browser, which gets the design's own compact page with the app's
 * banner instead: a phone-sized window ([LayoutKind.Phone], or a [LayoutKind.Tiny] sliver). Null for the desk page.
 */
internal fun OpenEntry.Desk.phoneStores(layout: WindowLayout): StoreLinks? = stores?.takeIf {
    it.phone && (layout.kind == LayoutKind.Phone || layout.kind == LayoutKind.Tiny)
}

/**
 * The shot sheet as a fold's page (the right one, over the picked video, the steps dimmed on the left) rather than a
 * sheet: the phone app on a book foldable held open. The desk page has no fold layout.
 */
internal fun shotSheetIsFoldPage(entry: OpenEntry, layout: WindowLayout): Boolean =
    entry is OpenEntry.PhoneApp && layout.fold

/**
 * The desk page's measures at [widthDp]: how the same page fits its width, not a different layout. The gutters grow
 * with the window between their bounds; from [TWO_UP_DP] the steps (and the notes) sit side by side, from
 * [PREVIEW_BESIDE_DP] the preview moves beside them.
 */
internal data class DeskMetrics(
    val gutterDp: Float,
    val topDp: Float,
    val previewGapDp: Float,
    val previewBeside: Boolean,
    val twoUp: Boolean,
)

internal fun deskMetrics(widthDp: Float): DeskMetrics =
    DeskMetrics(
        gutterDp = (widthDp * GUTTER_SHARE).coerceIn(MIN_GUTTER_DP, MAX_GUTTER_DP),
        topDp = (widthDp * TOP_SHARE).coerceIn(MIN_TOP_DP, MAX_TOP_DP),
        previewGapDp = (widthDp * GUTTER_SHARE).coerceIn(MIN_PREVIEW_GAP_DP, MAX_GUTTER_DP),
        previewBeside = widthDp >= PREVIEW_BESIDE_DP,
        twoUp = widthDp >= TWO_UP_DP,
    )

/** The canvas's gutters: 4.4 % of the width (64 at 1440), 3.3 % above the title. */
private const val GUTTER_SHARE = 0.044f
private const val TOP_SHARE = 0.033f
private const val MIN_GUTTER_DP = 16f
private const val MAX_GUTTER_DP = 64f
private const val MIN_TOP_DP = 24f
private const val MAX_TOP_DP = 48f
private const val MIN_PREVIEW_GAP_DP = 32f

/** Room for the steps beside the 300 dp preview, and for two steps (or three notes) side by side. */
internal const val PREVIEW_BESIDE_DP = 900f
internal const val TWO_UP_DP = 800f
