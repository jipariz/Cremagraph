package app.onlyshots.layout

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.runtime.Immutable
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
@Immutable
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
    /** The window is wider than tall. */
    val landscape: Boolean = false,
) {
    /**
     * `Stacked` with the window wider than tall: a book foldable resting on its side (tabletop), where a landscape
     * picture fills the upper half. Otherwise a flip in flex mode, whose upper half stands tall.
     */
    val onItsSide: Boolean
        get() = kind == LayoutKind.Stacked && landscape
}

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
        return stackedLayout(widthDp, heightDp, splitDp = start, hingeDp = end - start, directive = directive)
    }
    // A book foldable held open, flat or half open: the hinge is the divider, so nothing sits on top of it.
    val book = hinge?.takeIf { it.isVertical } ?: flatFold
    if (book != null && medium) {
        val (start, end) = book.span(density)
        return bookLayout(widthDp, heightDp, splitDp = start, hingeDp = end - start, directive = directive)
    }
    if (medium) return wideLayout(widthDp, heightDp, expanded, directive)
    val shortHeight = !size.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    if (shortHeight && widthDp < TINY_WIDTH_DP) return tinyLayout(widthDp, heightDp, directive)
    return phoneLayout(widthDp, heightDp, directive)
}

/*
 * One layout per posture, as [windowLayout] decides them. The screenshot previews and the UI tests build their postures
 * (a fold, a tabletop, a cover screen) with these too, since a test window can't fold, so they lay out as the real
 * decision would.
 */

/** A medium or expanded window without a fold: the first pane takes [MEDIUM_SPLIT] or [EXPANDED_SPLIT] of it. */
fun wideLayout(
    widthDp: Float,
    heightDp: Float,
    expanded: Boolean = widthDp >= EXPANDED_WIDTH_DP,
    directive: PaneScaffoldDirective = PaneScaffoldDirective.Default,
): WindowLayout {
    val share = if (expanded) EXPANDED_SPLIT else MEDIUM_SPLIT
    return WindowLayout(
        LayoutKind.Wide,
        splitDp = clampSplit(widthDp * share, widthDp, 0f),
        directive = directive,
        landscape = widthDp > heightDp,
    )
}

/** A book foldable or the iPhone Duo held open: two pages meeting at the fold, by default down the middle. */
fun bookLayout(
    widthDp: Float,
    heightDp: Float,
    splitDp: Float = widthDp / 2f,
    hingeDp: Float = 0f,
    directive: PaneScaffoldDirective = PaneScaffoldDirective.Default,
): WindowLayout =
    WindowLayout(
        LayoutKind.Wide,
        splitDp = clampSplit(splitDp, widthDp, hingeDp),
        hingeDp = hingeDp,
        directive = directive,
        fold = true,
        landscape = widthDp > heightDp,
    )

/** Half open with the fold across: tabletop when the window is wider than tall, else a flip in flex mode. */
fun stackedLayout(
    widthDp: Float,
    heightDp: Float,
    splitDp: Float = heightDp / 2f,
    hingeDp: Float = 0f,
    directive: PaneScaffoldDirective = PaneScaffoldDirective.Default,
): WindowLayout =
    WindowLayout(
        LayoutKind.Stacked,
        splitDp = clampSplit(splitDp, heightDp, hingeDp),
        hingeDp = hingeDp,
        directive = directive,
        landscape = widthDp > heightDp,
    )

/** A flip's cover screen, or a sliver of split screen. */
fun tinyLayout(
    widthDp: Float,
    heightDp: Float,
    directive: PaneScaffoldDirective = PaneScaffoldDirective.Default,
): WindowLayout = WindowLayout(LayoutKind.Tiny, directive = directive, landscape = widthDp > heightDp)

fun phoneLayout(
    widthDp: Float,
    heightDp: Float,
    directive: PaneScaffoldDirective = PaneScaffoldDirective.Default,
): WindowLayout = WindowLayout(LayoutKind.Phone, directive = directive, landscape = widthDp > heightDp)

/** Where the hinge starts and ends across the split, in dp. */
private fun HingeInfo.span(density: Float): Pair<Float, Float> =
    if (isVertical) bounds.left / density to bounds.right / density else bounds.top / density to bounds.bottom / density

private fun clampSplit(splitDp: Float, totalDp: Float, hingeDp: Float): Float {
    val max = totalDp - hingeDp - MIN_PANE_DP
    return if (max <= MIN_PANE_DP) totalDp / 2f else splitDp.coerceIn(MIN_PANE_DP, max)
}
