package app.onlyshots.layout

/** Where a wide window's panel goes: beside the picture, or under it across the whole width. */
enum class PanePlacement {
    Side,
    Below,
}

/**
 * The side panel's width beside a picture: the desk layout's [PANEL_WIDTH_DP], narrower on a medium window, where every
 * dp of picture counts.
 */
fun sidePanelWidthDp(widthDp: Float): Float =
    if (widthDp >= EXPANDED_WIDTH_DP) PANEL_WIDTH_DP else MEDIUM_PANEL_WIDTH_DP

/**
 * [WidePanes]' side panel when the screen asks for no width: half a medium window less the gap (as the adaptive
 * scaffold splits it: a list beside a form both need the room), and [PANEL_WIDTH_DP] on an expanded one.
 */
fun defaultPanelWidthDp(widthDp: Float, gapDp: Float): Float =
    if (widthDp < EXPANDED_WIDTH_DP) (widthDp - gapDp) / 2f else PANEL_WIDTH_DP

/** The largest [ratio] (width over height) frame that fits [widthDp] × [heightDp]; zero when either is not positive. */
fun fitRatio(widthDp: Float, heightDp: Float, ratio: Float): Pair<Float, Float> {
    if (widthDp <= 0f || heightDp <= 0f || ratio <= 0f) return 0f to 0f
    return if (widthDp / heightDp > ratio) heightDp * ratio to heightDp else widthDp to widthDp / ratio
}

/**
 * Where the panel leaves the picture the most room: a [frameRatio] frame fitted beside a [sidePanelDp] panel, or above
 * a [belowPanelDp] one, in a [widthDp] × [heightDp] window that also gives [chromeDp] of height to what always sits
 * over or under the picture (a top bar, a timeline). An option whose picture pane would be under [minMainDp] either way
 * is out; the side wins a tie, being what the panel's contents are drawn for.
 */
fun placePanel(
    widthDp: Float,
    heightDp: Float,
    frameRatio: Float,
    chromeDp: Float,
    sidePanelDp: Float = sidePanelWidthDp(widthDp),
    belowPanelDp: Float,
    minMainDp: Float = MIN_MAIN_DP,
): PanePlacement {
    val sideW = widthDp - sidePanelDp
    val sideH = heightDp - chromeDp
    val belowH = heightDp - chromeDp - belowPanelDp
    val side = if (sideW >= minMainDp && sideH >= minMainDp) area(fitRatio(sideW, sideH, frameRatio)) else -1f
    val below = if (belowH >= minMainDp) area(fitRatio(widthDp, belowH, frameRatio)) else -1f
    return if (below > side) PanePlacement.Below else PanePlacement.Side
}

/**
 * The panel under the picture: at least [minPanelDp], and whatever height the [frameRatio] frame cannot use above it (a
 * wide video on an upright tablet is as wide as the window and no taller), so nothing is left empty around it.
 */
fun belowPanelHeightDp(widthDp: Float, heightDp: Float, frameRatio: Float, chromeDp: Float, minPanelDp: Float): Float {
    val frameH = fitRatio(widthDp, heightDp - chromeDp - minPanelDp, frameRatio).second
    return maxOf(minPanelDp, heightDp - chromeDp - frameH)
}

private fun area(size: Pair<Float, Float>) = size.first * size.second
