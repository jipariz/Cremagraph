package app.onlyshots.layout

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.window.core.layout.WindowSizeClass

enum class LayoutKind {
    /** One column: a phone, or any window narrower than the medium width class (600 dp). */
    Phone,

    /** Two panes side by side: medium and expanded widths, a tablet, a book foldable held open, the desktop. */
    Wide,

    /** Two panes stacked: a foldable resting half-open (tabletop), either shape. */
    Stacked,

    /** Barely any room: a cover screen, or a sliver of split screen. */
    Tiny,
}

/**
 * What the window can hold. The handover's table has seven rows but only three layouts; the difference between a tablet
 * and a book foldable is where the split goes, not what is in it.
 */
data class WindowLayout(
    val kind: LayoutKind,
    /** Size of the first pane, in dp from the start or top edge. */
    val splitDp: Float = 0f,
    /** The hinge's own width; nothing may be drawn in it. */
    val hingeDp: Float = 0f,
    /**
     * The adaptive library's pane directive for this window (how many panes, the gap between them, the hinge to keep
     * clear), for the screens laid out with its pane scaffolds.
     */
    val directive: PaneScaffoldDirective = PaneScaffoldDirective.Default,
    /**
     * The split is a fold (a book foldable or the iPhone Duo open, flat or half open): the two panes meet at it, and
     * each screen gives them equal weight, picture on one side and its controls on the other.
     */
    val fold: Boolean = false,
    /**
     * `Stacked` with the window wider than tall: a book foldable resting on its side (tabletop), where a landscape
     * picture fills the upper half. Otherwise a flip in flex mode, whose upper half stands tall.
     */
    val onItsSide: Boolean = false,
)

/** Neither pane may be squeezed below this, whatever a stale posture claims. */
const val MIN_PANE_DP = 120f

/** Narrower than this with a compact height, and only the viewfinder and a stop button fit. */
const val TINY_WIDTH_DP = 400f

/**
 * The one decision every adaptive branch goes through, from the adaptive library's view of the window
 * (`currentWindowAdaptiveInfo()`): its width and height classes and its posture. [widthDp] and [heightDp] are the
 * window's size, for where a split without a hinge goes; [density] turns the hinge's pixels into dp.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun windowLayout(info: WindowAdaptiveInfo, widthDp: Float, heightDp: Float, density: Float = 1f): WindowLayout {
    val size = info.windowSizeClass
    val medium = size.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val expanded = size.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val hinges = info.windowPosture.hingeList
    val hinge = hinges.firstOrNull { it.isSeparating }
    // A fold lying flat divides nothing by itself, but a book open flat still reads as two pages.
    val flatFold = hinges.firstOrNull { it.isVertical && !it.isSeparating }
    val directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(info)
    // A hinge that is being folded, lying flat across the window: camera above it, controls below.
    if (hinge != null && !hinge.isVertical && (info.windowPosture.isTabletop || !hinge.isFlat)) {
        val (start, end) = hinge.span(density)
        return WindowLayout(
            LayoutKind.Stacked,
            splitDp = clampSplit(start, heightDp, end - start),
            hingeDp = end - start,
            directive = directive,
            onItsSide = widthDp > heightDp,
        )
    }
    // A book foldable held open, flat or half open: the hinge is the divider, so nothing sits on top of it.
    val book = hinge?.takeIf { it.isVertical } ?: flatFold
    if (book != null && medium) {
        val (start, end) = book.span(density)
        return WindowLayout(
            LayoutKind.Wide,
            splitDp = clampSplit(start, widthDp, end - start),
            hingeDp = end - start,
            directive = directive,
            fold = true,
        )
    }
    if (medium) {
        val share = if (expanded) 0.62f else 0.7f
        return WindowLayout(LayoutKind.Wide, splitDp = clampSplit(widthDp * share, widthDp, 0f), directive = directive)
    }
    val shortHeight = !size.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    if (shortHeight && widthDp < TINY_WIDTH_DP) return WindowLayout(LayoutKind.Tiny, directive = directive)
    return WindowLayout(LayoutKind.Phone, directive = directive)
}

/** Where the hinge starts and ends across the split, in dp. */
private fun HingeInfo.span(density: Float): Pair<Float, Float> =
    if (isVertical) bounds.left / density to bounds.right / density else bounds.top / density to bounds.bottom / density

private fun clampSplit(splitDp: Float, totalDp: Float, hingeDp: Float): Float {
    val max = totalDp - hingeDp - MIN_PANE_DP
    return if (max <= MIN_PANE_DP) totalDp / 2f else splitDp.coerceIn(MIN_PANE_DP, max)
}
