package app.onlyshots.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Layout {
    GRAPH_AND_READINGS,
    GRAPH_ONLY,
    READINGS_ONLY,
}

/**
 * A rectangle in normalized coordinates of the video frame (0..1 on each axis), whatever its [VideoFormat]. Sizes that
 * mean something to the eye (the minimum, the default, the margins) are reference px of the format's frame, 390 on its
 * short side; the 9:16 numbers are the ones this always had.
 */
@Serializable
data class NormRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float
        get() = x + w

    val bottom: Float
        get() = y + h

    /** Move by a normalized delta, keeping the whole rect inside the frame. */
    fun movedBy(dx: Float, dy: Float): NormRect =
        copy(x = (x + dx).coerceIn(0f, 1f - w), y = (y + dy).coerceIn(0f, 1f - h))

    /** A corner of the rect, the one a resize drags. */
    enum class Corner(val left: Boolean, val top: Boolean) {
        TOP_LEFT(true, true),
        TOP_RIGHT(false, true),
        BOTTOM_LEFT(true, false),
        BOTTOM_RIGHT(false, false),
    }

    /**
     * Resize by dragging [corner] by ([dx], [dy]): the opposite corner stays put, the size keeps the format's minimum,
     * and the dragged edges stay inside [within] (the whole frame by default).
     */
    context(_: VideoFormat)
    fun resizedFrom(corner: Corner, dx: Float, dy: Float, within: NormRect = Frame): NormRect {
        val (l, r) =
            if (corner.left) (x + dx).coerceIn(within.x, (right - minW()).coerceAtLeast(within.x)) to right
            else x to (right + dx).coerceIn((x + minW()).coerceAtMost(within.right), within.right)
        val (t, b) =
            if (corner.top) (y + dy).coerceIn(within.y, (bottom - minH()).coerceAtLeast(within.y)) to bottom
            else y to (bottom + dy).coerceIn((y + minH()).coerceAtMost(within.bottom), within.bottom)
        return NormRect(l, t, r - l, b - t)
    }

    /** Same size, clamped into the frame and to the minimum size. */
    context(_: VideoFormat)
    fun clamped(): NormRect {
        val cw = w.coerceIn(minW(), 1f)
        val ch = h.coerceIn(minH(), 1f)
        return NormRect(x.coerceIn(0f, 1f - cw), y.coerceIn(0f, 1f - ch), cw, ch)
    }

    /**
     * The same graph in a frame of another format: the same size in reference px (so it looks the same against the
     * short side), its centre where it was, and kept inside.
     */
    fun refit(from: VideoFormat, to: VideoFormat): NormRect {
        if (from == to) return this
        val nw = (w * from.refW / to.refW).coerceAtMost(1f - 2 * MARGIN_REF / to.refW)
        val nh = (h * from.refH / to.refH).coerceAtMost(1f)
        val cx = x + w / 2f
        val cy = y + h / 2f
        return context(to) { NormRect(cx - nw / 2f, cy - nh / 2f, nw, nh).clamped() }
    }

    /**
     * The same rect kept inside [bounds] (a part of the frame, such as what a screen shows of it): moved in, and made
     * smaller only where it is larger than they are.
     */
    fun within(bounds: NormRect): NormRect {
        val nw = w.coerceAtMost(bounds.w)
        val nh = h.coerceAtMost(bounds.h)
        // The upper limit never under the lower one: a rect as wide as the bounds rounds either way.
        val nx = x.coerceIn(bounds.x, maxOf(bounds.x, bounds.right - nw))
        val ny = y.coerceIn(bounds.y, maxOf(bounds.y, bounds.bottom - nh))
        return NormRect(nx, ny, nw, nh)
    }

    /** Whether this rect lies inside [bounds], give or take [slack]. */
    fun inside(bounds: NormRect, slack: Float = 0f): Boolean =
        x >= bounds.x - slack &&
            y >= bounds.y - slack &&
            right <= bounds.right + slack &&
            bottom <= bounds.bottom + slack

    companion object {
        const val REF_W = 390f
        const val REF_H = REF_W * 16f / 9f

        /** Minimum overlay size at 9:16, 120 × 70 at the 390-wide reference ([minW] / [minH] for any format). */
        const val MIN_W = 120f / REF_W
        const val MIN_H = 70f / REF_H

        /** The side margin, reference px. */
        const val MARGIN_REF = 14f

        context(format: VideoFormat)
        fun minW(): Float = 120f / format.refW

        context(format: VideoFormat)
        fun minH(): Float = 70f / format.refH

        /**
         * Where a new graph goes in [format]: at 9:16 the record screen's bottom placement, clear of a sharing app's
         * captions; elsewhere along the bottom, 14 px in, no wider than in portrait (bottom left at 16:9).
         */
        fun default(format: VideoFormat): NormRect {
            if (format == VideoFormat.PORTRAIT_9_16) return Default
            val w = minOf(format.refW - 2 * MARGIN_REF, 362f)
            val h = 170f
            return NormRect(
                MARGIN_REF / format.refW,
                (format.refH - h - MARGIN_REF) / format.refH,
                w / format.refW,
                h / format.refH,
            )
        }

        fun ofReference(x: Float, y: Float, w: Float, h: Float): NormRect =
            NormRect(x / REF_W, y / REF_H, w / REF_W, h / REF_H)

        /** Default placement from the Record screen: bottom, full width minus margins. */
        val Default: NormRect = ofReference(14f, 440f, 362f, 170f)

        /** The whole frame. */
        val Frame: NormRect = NormRect(0f, 0f, 1f, 1f)
    }
}

enum class SnapPosition {
    TOP,
    MIDDLE,
    BOTTOM,
}

/** Snap presets from the overlay sheet: top, middle, or bottom clear of the record bar. */
fun NormRect.snappedTo(position: SnapPosition): NormRect {
    val margin = 14f / NormRect.REF_W
    val yRef =
        when (position) {
            SnapPosition.TOP -> 14f
            SnapPosition.MIDDLE -> (NormRect.REF_H - h * NormRect.REF_H) / 2f
            SnapPosition.BOTTOM -> NormRect.REF_H - h * NormRect.REF_H - 70f
        }
    return copy(x = margin.coerceAtMost(1f - w), y = (yRef / NormRect.REF_H).coerceIn(0f, 1f - h))
}

@Serializable
data class OverlaySettings(
    /** A style id, or [STYLE_AUTO] for the source's default style. */
    val styleId: String = STYLE_AUTO,
    /**
     * A copy of a user's own style, kept with the video: it looks the same after the style is edited or deleted, and
     * the web editor, which has no access to the phone's styles, can draw it. Null for the built-in styles.
     */
    val style: app.onlyshots.core.model.style.GraphStyle? = null,
    val series: Set<Series> = DefaultSeries,
    val layout: Layout = Layout.GRAPH_AND_READINGS,
    val rect: NormRect = NormRect.Default,
    /** 0.2..1.0 in 0.05 steps. */
    val opacity: Float = 1f,
    /** The legend at the right of the graph's header; off leaves the title the whole row. */
    val legend: Boolean = true,
    /** The phase lines and their labels across the chart, where the shot has phases. */
    val phases: Boolean = true,
    /** The graph's title as the user wrote it; null for "profile · machine" (see [overlayTitle]). */
    val title: String? = null,
    /** A line under the title: dose, grind, beans. */
    val recipe: String? = null,
) {
    init {
        require(opacity in MIN_OPACITY..1f) { "opacity $opacity out of range" }
    }

    fun withOpacity(value: Float): OverlaySettings = copy(opacity = snapOpacity(value))

    /**
     * The graph for a recording from [kind]. A scale gives weight and weight flow only, so its graph adds weight flow
     * to the default series, which would otherwise leave it a single line; a set the user chose is kept as it is.
     */
    fun forSource(kind: SourceKind): OverlaySettings =
        if (kind == SourceKind.SCALE && series == DefaultSeries) copy(series = series + Series.WEIGHT_FLOW) else this

    companion object {
        const val STYLE_AUTO = "auto"
        /** The background can go all the way: at 0 only the curves and readings sit on the video. */
        const val MIN_OPACITY = 0f
        val DefaultSeries: Set<Series> = setOf(Series.PRESSURE, Series.FLOW, Series.WEIGHT, Series.TARGETS)

        fun snapOpacity(value: Float): Float =
            ((kotlin.math.round(value.coerceIn(MIN_OPACITY, 1f) * 20f)) / 20f).coerceIn(MIN_OPACITY, 1f)
    }
}
