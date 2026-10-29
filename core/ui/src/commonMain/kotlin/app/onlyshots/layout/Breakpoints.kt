package app.onlyshots.layout

import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/*
 * Every size the adaptive layouts decide on, in one place. Screens read the decided [WindowLayout], never these
 * against their own measurements; only the layout code in this package compares sizes.
 */

/** From here a window is expanded (the adaptive library's breakpoint): the side panel gets its full width. */
const val EXPANDED_WIDTH_DP = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat()

/** The side panel's width on an expanded window, as the web editor's desk layout has it. */
const val PANEL_WIDTH_DP = 380f

/** The side panel beside a picture on a medium window, where every dp of picture counts. */
const val MEDIUM_PANEL_WIDTH_DP = 340f

val PANEL_WIDTH = PANEL_WIDTH_DP.dp

/** How much of the window the first pane takes without a hinge: expanded, and medium. */
const val EXPANDED_SPLIT = 0.62f
const val MEDIUM_SPLIT = 0.7f

/** Neither pane may be squeezed below this, whatever a stale posture claims. */
const val MIN_PANE_DP = 120f

/** Narrower than this with a compact height, and only the viewfinder and a stop button fit. */
const val TINY_WIDTH_DP = 400f

/** The least picture pane worth having: under it, the other panel placement wins whatever its area. */
const val MIN_MAIN_DP = 240f

/** A sheet over a picture on a phone or a tabletop's half rises at most this far up the window. */
const val SHEET_HEIGHT_FRACTION = 0.72f
