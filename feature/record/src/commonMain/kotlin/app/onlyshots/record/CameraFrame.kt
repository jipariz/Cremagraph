package app.onlyshots.record

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.VideoFormat

/**
 * The camera's whole frame covering a pane, [width] × [height] in the pane's unit (dp), and what of it is on screen
 * ([shown], in the frame's fractions): on a pane longer than the frame its edges are cut off, though not in the video.
 */
internal data class CameraFrame(val width: Float, val height: Float, val shown: NormRect)

/** The frame of aspect [ratio] covering a pane of [paneW] × [paneH]. */
internal fun cameraFrame(paneW: Float, paneH: Float, ratio: Float): CameraFrame {
    val width = if (paneW / paneH > ratio) paneW else paneH * ratio
    val height = width / ratio
    val shownW = (paneW / width).coerceAtMost(1f)
    val shownH = (paneH / height).coerceAtMost(1f)
    return CameraFrame(width, height, NormRect((1f - shownW) / 2f, (1f - shownH) / 2f, shownW, shownH))
}

/** Where the graph may go, in the frame's fractions: what of the frame is [shown], a side margin in. */
context(capture: VideoFormat)
internal fun graphBounds(shown: NormRect): NormRect {
    val mx = NormRect.MARGIN_REF / capture.refW
    val my = NormRect.MARGIN_REF / capture.refH
    val left = shown.x + mx
    val right = shown.right - mx
    val top = shown.y + my
    val bottom = shown.bottom - my
    // Never less room than the smallest graph: a sliver of window keeps the whole frame instead.
    if (right - left < NormRect.minW() || bottom - top < NormRect.minH()) return NormRect.Frame
    return NormRect(left, top, right - left, bottom - top)
}

/** What of the [frame] (root px) is on screen: its [shown] fractions. */
internal fun shownIn(frame: Rect, shown: NormRect): Rect =
    Rect(
        frame.left + frame.width * shown.x,
        frame.top + frame.height * shown.y,
        frame.left + frame.width * shown.right,
        frame.top + frame.height * shown.bottom,
    )

/** The padding that brings the frame in to the part of it on screen. */
internal fun CameraFrame.shownPadding(): PaddingValues =
    PaddingValues(
        start = (width * shown.x).dp,
        top = (height * shown.y).dp,
        end = (width * (1f - shown.right)).dp,
        bottom = (height * (1f - shown.bottom)).dp,
    )

/**
 * Whether a graph at [rect] must be moved into [bounds]: one the screen would cut off is moved where it shows, and kept
 * there, since the video draws it as placed. Not mid-take, and not without data, where no graph is drawn.
 */
internal fun graphOutside(rect: NormRect, bounds: NormRect, recording: Boolean, hasData: Boolean): Boolean =
    !recording && hasData && !rect.inside(bounds, BOUNDS_SLACK)

/** How far out a graph may stand before it is moved in: what moving it between frames rounds off. */
private const val BOUNDS_SLACK = 0.005f
