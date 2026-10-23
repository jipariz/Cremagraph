package app.onlyshots.style

import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.style_tab_grid
import app.onlyshots.core.resources.style_tab_lines
import app.onlyshots.core.resources.style_tab_panel
import app.onlyshots.core.resources.style_tab_readings
import app.onlyshots.core.resources.style_tab_text
import app.onlyshots.layout.EXPANDED_WIDTH_DP
import org.jetbrains.compose.resources.StringResource

/*
 * The style editor's decisions, apart from how it draws them: which frame shows, what the preview's caption says, how
 * a name is typed and kept, which sheet is up, how wide the panel is. Tested in StyleEditorRulesTest.
 */

/** The four parts of a style, one tab each. */
enum class StyleTab(val label: StringResource) {
    PANEL(Res.string.style_tab_panel),
    LINES(Res.string.style_tab_lines),
    GRID(Res.string.style_tab_grid),
    TEXT(Res.string.style_tab_text),
    READINGS(Res.string.style_tab_readings),
}

/**
 * Which colour of the style the colour sheet is changing, and whether it carries its own opacity. [panelSwatches]: the
 * panel colours are offered under the presets (a colour drawn over the video with an opacity, except the grid's).
 */
class ColorTarget(
    val title: UiText,
    val get: (GraphStyle) -> Int,
    val set: (GraphStyle, Int) -> GraphStyle,
    val withOpacity: Boolean = false,
    val panelSwatches: Boolean = withOpacity,
)

/** The one sheet over the controls: saving, else a colour being picked. */
sealed interface StyleSheet {
    data object Save : StyleSheet

    data class Color(val target: ColorTarget) : StyleSheet
}

/** Saving wins over a colour: one sheet slides up at a time. */
fun styleSheet(saving: Boolean, color: ColorTarget?): StyleSheet? =
    if (saving) StyleSheet.Save else color?.let(StyleSheet::Color)

/** [frame] moved [by] frames, kept on one of [count] frames (0 when there are none). */
fun stepFrame(frame: Int, by: Int, count: Int): Int = (frame + by).coerceIn(0, (count - 1).coerceAtLeast(0))

/** A drag's progress through the frames: whole [steps] to take now, and the [remainder] carried to the next move. */
data class FrameDrag(val steps: Int, val remainder: Float)

/**
 * A sideways drag of [dx] px on top of [carried] px, a frame per [stepPx]: dragging right goes back, left goes on, so
 * the picture follows the finger like a strip of film.
 */
fun dragFrames(carried: Float, dx: Float, stepPx: Float): FrameDrag {
    var acc = carried + dx
    var steps = 0
    while (acc > stepPx) {
        steps--
        acc -= stepPx
    }
    while (acc < -stepPx) {
        steps++
        acc += stepPx
    }
    return FrameDrag(steps, acc)
}

/** What the preview's caption says the picture is. */
enum class PreviewCaption {
    /** The live picture's own caption (the camera, or the video playing). */
    LIVE,

    /** Frames of the video: drag to try others. */
    DRAG,

    /** No video yet: record one to preview on. */
    RECORD,
}

fun previewCaption(live: Boolean, frameCount: Int): PreviewCaption =
    when {
        live -> PreviewCaption.LIVE
        frameCount > 1 -> PreviewCaption.DRAG
        else -> PreviewCaption.RECORD
    }

/** A still preview with more than one frame can be dragged through them. */
fun canDragFrames(live: Boolean, frameCount: Int): Boolean = !live && frameCount > 1

/** The longest name a style takes, in the header and the save sheet alike. */
const val STYLE_NAME_MAX = 32

/** What is typed into a name field, cut to [STYLE_NAME_MAX]. */
fun typedName(text: String): String = text.take(STYLE_NAME_MAX)

/** The name to rename [current] to from what was [typed]; null when there is nothing to change. */
fun renamed(typed: String, current: String): String? = typed.trim().takeIf { it.isNotEmpty() && it != current }

/**
 * The saturation and brightness the hue strip paints at: the colour's own, unless it is too grey or too dark for the
 * hues to show, then a middling one.
 */
fun hueStripTone(saturation: Float, value: Float): Pair<Float, Float> =
    (if (saturation < GREY_SATURATION) STRIP_SATURATION else saturation) to
        (if (value < DARK_VALUE) STRIP_VALUE else value)

/** How the readings' values are coloured, as the segmented control offers it. */
enum class ValuesMode {
    SERIES,
    WHITE,
    CUSTOM,
}

/** Which of [ValuesMode] [s]'s value colours are: one of the two presets, else custom. */
fun valuesMode(s: GraphStyle): ValuesMode =
    when (s.cells.values) {
        s.withSeriesValues().cells.values -> ValuesMode.SERIES
        s.withWhiteValues().cells.values -> ValuesMode.WHITE
        else -> ValuesMode.CUSTOM
    }

/**
 * The controls' panel width on a wide window [widthDp] wide: [STYLE_PANEL_WIDTH_DP] once it is expanded; null below,
 * where the two share it as the adaptive scaffold splits it.
 */
fun stylePanelWidthDp(widthDp: Float): Float? = STYLE_PANEL_WIDTH_DP.takeIf { widthDp >= EXPANDED_WIDTH_DP }

/** The controls' panel beside the preview on an expanded window. */
const val STYLE_PANEL_WIDTH_DP = 420f

private const val GREY_SATURATION = 0.15f
private const val STRIP_SATURATION = 0.6f
private const val DARK_VALUE = 0.3f
private const val STRIP_VALUE = 0.9f
