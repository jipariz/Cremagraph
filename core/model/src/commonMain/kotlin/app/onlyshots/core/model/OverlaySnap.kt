package app.onlyshots.core.model

/**
 * Where a dragged graph snaps. At 9:16, the same targets as the web editor: below the area a sharing app's header
 * covers, the middle, above the area its buttons and captions cover. In the other formats, which no app covers like
 * that, the top margin, the middle and the bottom margin. Across: the left margin, the centre, the right margin.
 */
object OverlaySnap {
    /** Share of the frame a sharing app's header covers at the top. */
    const val SAFE_TOP = 0.12f

    /** Share of the frame its buttons and captions cover at the bottom. */
    const val SAFE_BOTTOM = 0.20f

    /** Side margin, 14 px at the 390-wide reference. */
    const val MARGIN = 14f / NormRect.REF_W

    /** Where a target is; the screens name it (Place.TOP_BELOW_HEADER, Place.CENTRE). */
    enum class Place {
        TOP_BELOW_HEADER,
        TOP,
        MIDDLE,
        BOTTOM_ABOVE_CAPTIONS,
        BOTTOM,
        LEFT,
        CENTRE,
        RIGHT,
    }

    /** A snap that took: where it is, and where its guide line runs (0..1 across the frame). */
    data class Target(val place: Place, val guide: Float)

    data class Snapped(val rect: NormRect, val vertical: Target?, val horizontal: Target?)

    /**
     * [free] pulled onto the nearest target within [pullX] / [pullY] (normalized). Drags keep their own unsnapped rect
     * and pass it here every frame, so a snap never traps the graph: past the pull distance it lets go.
     */
    context(format: VideoFormat)
    fun snap(free: NormRect, pullX: Float, pullY: Float): Snapped {
        val margin = NormRect.MARGIN_REF / format.refW
        val marginY = NormRect.MARGIN_REF / format.refH
        val vertical =
            (if (format == VideoFormat.PORTRAIT_9_16)
                    listOf(
                        SAFE_TOP to Target(Place.TOP_BELOW_HEADER, SAFE_TOP),
                        (1f - free.h) / 2f to Target(Place.MIDDLE, 0.5f),
                        1f - SAFE_BOTTOM - free.h to Target(Place.BOTTOM_ABOVE_CAPTIONS, 1f - SAFE_BOTTOM),
                    )
                else
                    listOf(
                        marginY to Target(Place.TOP, marginY),
                        (1f - free.h) / 2f to Target(Place.MIDDLE, 0.5f),
                        1f - marginY - free.h to Target(Place.BOTTOM, 1f - marginY),
                    ))
                .filter { (y, _) -> kotlin.math.abs(free.y - y) <= pullY }
                .minByOrNull { (y, _) -> kotlin.math.abs(free.y - y) }
        val horizontal =
            listOf(
                    margin to Target(Place.LEFT, margin),
                    (1f - free.w) / 2f to Target(Place.CENTRE, 0.5f),
                    1f - margin - free.w to Target(Place.RIGHT, 1f - margin),
                )
                .filter { (x, _) -> kotlin.math.abs(free.x - x) <= pullX }
                .minByOrNull { (x, _) -> kotlin.math.abs(free.x - x) }
        val rect = free.copy(x = horizontal?.first ?: free.x, y = vertical?.first ?: free.y).clamped()
        return Snapped(rect, vertical?.second, horizontal?.second)
    }
}
