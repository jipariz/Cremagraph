package app.onlyshots.edit

import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.edit_data
import app.onlyshots.core.resources.edit_export
import app.onlyshots.core.resources.edit_making_video
import app.onlyshots.core.resources.edit_phases_count
import app.onlyshots.core.resources.edit_recorded_with_video
import app.onlyshots.core.resources.edit_saved
import app.onlyshots.core.resources.edit_shot
import app.onlyshots.core.resources.edit_style
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.text
import app.onlyshots.layout.LayoutKind
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource

/**
 * What the editor shows around the picture, decided once from its state and the window: which layout, whether the
 * export covers the editor, which sheet is up. Every layout reads these rather than deciding for itself.
 */
data class EditChrome(
    /** The layout drawn: the window's, except that `Wide` and `Stacked` wait for the video on the phone's. */
    val kind: LayoutKind,
    /** The wide panel holds the graph's settings (the graph is selected), else the video's. */
    val graphPanel: Boolean,
    /** The export finished, and its result has not been seen yet. */
    val exportDone: Boolean,
    /**
     * The export covers the editor: on a wide window while it runs and until its result is seen; elsewhere while it
     * runs and until a saved video is seen to (shared, the screen moves on at once). The editor takes no keys under it.
     */
    val exportOverlay: Boolean,
    /** The bottom sheet shown: none while the export's own screen stands in for it. */
    val sheet: EditSheet?,
    /** Playback and the export carry the video's sound. */
    val sound: Boolean,
    /** Exported and handed to the share sheet: on to the next shot. A wide window stays and says so. */
    val leave: Boolean,
) {
    /** What Escape does: put down the export's result on a wide window, else the selected graph. */
    fun escape(graphSelected: Boolean): EditEscape =
        when {
            kind == LayoutKind.Wide && exportOverlay -> if (exportDone) EditEscape.ACKNOWLEDGE else EditEscape.NONE
            graphSelected -> EditEscape.DESELECT
            else -> EditEscape.NONE
        }
}

enum class EditEscape {
    NONE,
    ACKNOWLEDGE,
    DESELECT,
}

fun editChrome(
    windowKind: LayoutKind,
    ui: EditUiState,
    muted: Boolean,
    graphSelected: Boolean,
    reframing: Boolean,
): EditChrome {
    val video = ui.item
    val kind =
        when {
            windowKind == LayoutKind.Tiny -> LayoutKind.Tiny
            video == null -> LayoutKind.Phone
            else -> windowKind
        }
    val wide = kind == LayoutKind.Wide
    val exportDone = ui.savedToGallery || ui.exported
    val overlay =
        if (wide) ui.exporting != null || exportDone else video != null && (ui.exporting != null || ui.savedToGallery)
    return EditChrome(
        kind = kind,
        graphPanel = graphSelected && video?.shot != null && !reframing,
        exportDone = exportDone,
        exportOverlay = overlay,
        sheet = ui.sheet.takeUnless { overlay },
        sound = playsSound(muted, video),
        leave = ui.exported && !wide,
    )
}

/** Sound unless the take is muted, or was recorded without the microphone. */
fun playsSound(muted: Boolean, video: VideoItem?): Boolean = !muted && video?.hasAudio == true

/** The graph's pages, the wide panel's while the graph is selected. The shot and export are the video panel's. */
val GraphPages = listOf(EditSheet.STYLE, EditSheet.DATA)

/**
 * The graph's page the wide panel shows: the sheet when it is one of the graph's, else the page shown last, so the page
 * stays where it was while something else (an export) borrows the sheet.
 */
fun graphPage(sheet: EditSheet?, last: EditSheet): EditSheet = sheet?.takeIf { it in GraphPages } ?: last

/** A sheet's name, as its title and the wide panel's tabs say it. */
val EditSheet.label: StringResource
    get() =
        when (this) {
            EditSheet.STYLE -> Res.string.edit_style
            EditSheet.DATA -> Res.string.edit_data
            EditSheet.SHOT -> Res.string.edit_shot
            EditSheet.EXPORT -> Res.string.edit_export
        }

/** A bottom sheet's title: the export's says how the export is going. */
fun sheetTitle(sheet: EditSheet, ui: EditUiState): UiText =
    when {
        sheet != EditSheet.EXPORT -> sheet.label
        ui.exporting != null -> Res.string.edit_making_video
        ui.savedToGallery -> Res.string.edit_saved
        else -> Res.string.edit_export
    }.text()

/** "0:40.0 · 3 phases · lined up by the clock": the shot under its name; null without a shot. */
fun shotDetail(video: VideoItem): UiText? {
    val shot = video.shot ?: return null
    val phases = shot.phaseStartsUs.size
    return UiText.Joined(
        listOfNotNull(
            clock(shot.durationUs).raw(),
            Res.string.edit_phases_count.text("$phases").takeIf { phases > 0 },
            (if (video.syncLocked) Res.string.edit_recorded_with_video else shotStatus(video)).text(),
        )
    )
}

/** What is being edited, as the bars name it: the shot, else the file. */
fun editTitle(video: VideoItem): UiText = (video.shot?.profileName ?: video.file).raw()

/** The shot as the top bar's chip marks it with its dot. */
enum class ShotMark {
    /** No shot yet. */
    NONE,

    /** The shot's start is placed in the video. */
    PLACED,

    /** A shot whose start is not placed yet. */
    UNPLACED,
}

fun shotMark(video: VideoItem?): ShotMark =
    when {
        video?.shot == null -> ShotMark.NONE
        video.shotStartInVideoUs != null -> ShotMark.PLACED
        else -> ShotMark.UNPLACED
    }

/** The graph's opacity in whole percent, as its keys say it. */
fun opacityPercent(opacity: Float): Int = (opacity * 100).roundToInt()

/** The format pickers' order: the formats as [VideoFormat] lists them. */
fun formatIndex(format: VideoFormat): Int = VideoFormat.entries.indexOf(format)

fun formatAt(index: Int): VideoFormat = VideoFormat.entries[index]

/** How far the timeline's zoom key zooms in: four times the fitted strip. */
const val ZOOM_IN_FACTOR = 4f

/** The timeline's zoom key: in from fitted, back to fitted from any zoom. */
fun nextZoom(zoom: Float?): Float? = if (zoom == null) ZOOM_IN_FACTOR else null

/**
 * What sits on the picture in each layout: the phone has the reframe switch and the graph's toolbar on it, a wide
 * window has both in its panel, and a tabletop's picture carries the formats while framing.
 */
data class PictureChrome(
    /** The formats over the picture while framing. */
    val formatStrip: Boolean,
    /** The toolbar over the selected graph. */
    val toolbar: Boolean,
    /** The reframe switch at the picture's top right, while not framing. */
    val frameSwitch: Boolean,
    /** Where the framing hint shows, and so which way it slides in. */
    val hint: HintPlace,
)

enum class HintPlace {
    /** Top left, sliding down: where "done" is under the picture (the phone). */
    TOP_START,

    /** Bottom middle, sliding up. */
    BOTTOM_CENTER,
}

fun pictureChrome(kind: LayoutKind): PictureChrome =
    when (kind) {
        LayoutKind.Wide -> PictureChrome(false, toolbar = false, frameSwitch = false, hint = HintPlace.BOTTOM_CENTER)
        LayoutKind.Stacked -> PictureChrome(true, toolbar = false, frameSwitch = false, hint = HintPlace.BOTTOM_CENTER)
        LayoutKind.Phone,
        LayoutKind.Tiny -> PictureChrome(false, toolbar = true, frameSwitch = true, hint = HintPlace.TOP_START)
    }
