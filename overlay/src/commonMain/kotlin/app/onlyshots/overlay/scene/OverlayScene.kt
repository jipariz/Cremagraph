package app.onlyshots.overlay.scene

import app.onlyshots.overlay.Argb
import app.onlyshots.overlay.Dash
import app.onlyshots.overlay.Shadow

/** Pixel rectangle in the coordinate space of the video frame being drawn. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    fun inset(dx: Float, dy: Float = dx): Box = Box(left + dx, top + dy, right - dx, bottom - dy)
}

enum class TextAlign {
    START,
    CENTER,
    END,
}

/**
 * Drawing primitives. A scene is a flat list drawn back to front; every platform renderer draws exactly these and
 * nothing else, so preview, editor and export show the same pixels.
 */
sealed interface Prim

/**
 * Filled rounded rect, or a stroked outline when [stroke] > 0. With [gradient] the fill runs top to bottom through its
 * stops instead of [color]; two stops at the same position make a hard step (a gloss line).
 */
data class RectPrim(
    val box: Box,
    val color: Argb,
    val radius: Float = 0f,
    val stroke: Float = 0f,
    val gradient: List<GradientStop>? = null,
) : Prim

data class GradientStop(val at: Float, val color: Argb)

/**
 * "Blur the video behind this rounded rect by [blur] px" — or, with [glass], turn it into Liquid Glass with those
 * settings (its frost already in [blur], its corner in [radius]) and [tint]. Drawn first and never by the overlay
 * painter itself: the preview hands it to Haze over the live picture, each exporter draws it into the decoded frame
 * with the same glass shader.
 */
data class BackdropPrim(
    val box: Box,
    val radius: Float,
    val blur: Float,
    val glass: app.onlyshots.core.model.style.GlassSettings? = null,
    val tint: Argb = 0,
) : Prim

/** Open polyline; [points] holds x0, y0, x1, y1, … Equal when the points are, so scenes compare whole. */
class PolylinePrim(val points: FloatArray, val color: Argb, val width: Float, val dash: Dash? = null) : Prim {
    val pointCount: Int
        get() = points.size / 2

    override fun equals(other: Any?): Boolean =
        other is PolylinePrim &&
            points.contentEquals(other.points) &&
            color == other.color &&
            width == other.width &&
            dash == other.dash

    override fun hashCode(): Int =
        ((points.contentHashCode() * 31 + color) * 31 + width.hashCode()) * 31 + dash.hashCode()
}

/** Polygon from the polyline [points] down to [baseline] and back. */
class AreaPrim(val points: FloatArray, val baseline: Float, val color: Argb) : Prim {
    override fun equals(other: Any?): Boolean =
        other is AreaPrim && points.contentEquals(other.points) && baseline == other.baseline && color == other.color

    override fun hashCode(): Int = (points.contentHashCode() * 31 + baseline.hashCode()) * 31 + color
}

/** Single line of Open Sans text; [baseline] is the y of the first baseline, [x] the anchor for [align]. */
data class TextPrim(
    val text: String,
    val x: Float,
    val baseline: Float,
    val size: Float,
    val weight: Int,
    val color: Argb,
    val align: TextAlign = TextAlign.START,
    val shadow: Shadow? = null,
) : Prim

class OverlayScene(val bounds: Box, val prims: List<Prim>)

/** Text measurement supplied by the platform renderer (Compose text measurer, or a fake in tests). */
interface TextMetrics {
    fun width(text: String, size: Float, weight: Int): Float

    /** Distance from the top of the line box to the baseline. */
    fun ascent(size: Float): Float

    /** Distance from the baseline to the bottom of the line box. */
    fun descent(size: Float): Float
}
