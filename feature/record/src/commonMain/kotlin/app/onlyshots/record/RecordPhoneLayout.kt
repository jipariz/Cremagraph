package app.onlyshots.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.WindowLayout

/**
 * A phone, a tablet, an open foldable: the camera ([pane]) fills the window, under the system bars too, and the
 * controls float over it, as [placement] puts them. Nothing around the picture changes its size, which would rebind the
 * camera. While the graph is moved ([graphMoving]) the controls fade out of its way.
 *
 * [zoom] is the lenses' strip, standing (`vertical`) on its side. [onClear] gets what of the window, in root px, the
 * controls leave free: the graph is kept there.
 */
@Composable
internal fun RecordPhoneLayout(
    layout: WindowLayout,
    take: TakeInputs,
    placement: ControlsPlacement,
    /** The graph is in the upper half: the status chip goes over the bottom controls, out of its way. */
    graphOnTop: Boolean,
    graphMoving: Boolean,
    pane: @Composable (Modifier, PaneChrome) -> Unit,
    zoom: @Composable (Modifier, vertical: Boolean) -> Unit,
    onClear: (Rect) -> Unit,
) {
    pane(Modifier.fillMaxSize(), PaneChrome.Floating)
    val shown by animateFloatAsState(if (graphMoving) 0f else 1f, CgMotion.short(), label = "controls")
    val tracker = remember { ControlsTracker() }
    SideEffect { tracker.update(placement, onClear) }
    LaunchedEffect(placement) { tracker.report() }
    Box(Modifier.graphicsLayer { alpha = shown }.fillMaxSize().then(tracker.track(ControlSlot.Window))) {
        when (placement) {
            ControlsPlacement.Fold -> FoldControls(take, layout.splitDp.dp, layout.hingeDp.dp, zoom, tracker)
            ControlsPlacement.Landscape ->
                LandscapeControls(take, edgeFor(layout.kind == LayoutKind.Wide), zoom, tracker)
            ControlsPlacement.TabletUpright -> TabletUprightControls(take, graphOnTop, zoom, tracker)
            ControlsPlacement.PhoneUpright -> PhoneUprightControls(take, graphOnTop, zoom, tracker)
        }
    }
}

/**
 * An open book foldable: never across the fold. The top row, the status and the rules on the left page; the lenses and
 * the key at the foot of the right page, where the graph is kept. The pages are measured from the window's edge, as the
 * layout is.
 */
@Composable
private fun FoldControls(
    take: TakeInputs,
    split: Dp,
    hinge: Dp,
    zoom: @Composable (Modifier, vertical: Boolean) -> Unit,
    tracker: ControlsTracker,
) {
    Box(Modifier.fillMaxHeight().width(split)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(CgSpace.S16),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
        ) {
            TakeTopRow(take, back = true, tracker.track(ControlSlot.Top))
            Banners(take.camera, take.saveError)
            TakeStatusIfWorthIt(take.state)
            Spacer(Modifier.weight(1f))
            RulesCard(take, Modifier.fillMaxWidth())
        }
    }
    Box(Modifier.fillMaxSize().padding(start = split + hinge).then(tracker.track(ControlSlot.Page))) {
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(CgSpace.S16)
                .then(tracker.track(ControlSlot.Bottom)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
        ) {
            zoom(Modifier, false)
            KeyRow(take, Modifier.fillMaxWidth())
        }
    }
}

/**
 * On its side: a shade down the trailing edge, and on it the lenses standing over the key, "simulate shot" under it;
 * the status at the top on that side, the rules at the bottom on the start.
 */
@Composable
private fun BoxScope.LandscapeControls(
    take: TakeInputs,
    edge: Dp,
    zoom: @Composable (Modifier, vertical: Boolean) -> Unit,
    tracker: ControlsTracker,
) {
    Box(
        Modifier.align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(SIDE_SHADE)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, SHADE)))
    )
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(edge)) {
        TopControls(take, tracker, status = true, rules = false, Modifier.align(Alignment.TopStart), Alignment.End)
        Column(
            Modifier.align(Alignment.CenterEnd).then(tracker.track(ControlSlot.Side)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
        ) {
            zoom(Modifier, true)
            RecordKey(take)
            if (take.canSimulate) SimulatePill(take)
        }
        Column(
            Modifier.align(Alignment.BottomStart).widthIn(max = RULES_WIDTH).then(tracker.track(ControlSlot.Bottom)),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
        ) {
            RulesCard(take)
        }
    }
}

/** A tablet held upright: the lenses, the key and the rules float at the foot of the picture, centred. */
@Composable
private fun TabletUprightControls(
    take: TakeInputs,
    graphOnTop: Boolean,
    zoom: @Composable (Modifier, vertical: Boolean) -> Unit,
    tracker: ControlsTracker,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(edgeFor(wide = true))) {
        TopControls(take, tracker, status = !graphOnTop, rules = false, Modifier.align(Alignment.TopStart))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(tracker.track(ControlSlot.Bottom)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
        ) {
            if (graphOnTop) TakeStatusIfWorthIt(take.state, Modifier.align(Alignment.Start))
            zoom(Modifier, false)
            KeyRow(take, Modifier.widthIn(max = RULES_WIDTH).fillMaxWidth())
            RulesCard(take, Modifier.widthIn(max = RULES_WIDTH))
        }
    }
}

/**
 * A phone held upright: nothing across the picture but a shade at its foot; the lenses over the record key (and
 * "simulate shot" to its right) float there. The status and the rules float as cards out of the graph's way: under the
 * top row while the graph is low, over the lenses while it is high.
 */
@Composable
private fun BoxScope.PhoneUprightControls(
    take: TakeInputs,
    graphOnTop: Boolean,
    zoom: @Composable (Modifier, vertical: Boolean) -> Unit,
    tracker: ControlsTracker,
) {
    Box(
        Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(FOOT_SHADE)
            .background(Brush.verticalGradient(listOf(Color.Transparent, SHADE)))
    )
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(edgeFor(wide = false))) {
        TopControls(take, tracker, status = !graphOnTop, rules = !graphOnTop, Modifier.align(Alignment.TopStart))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            if (graphOnTop) {
                TakeStatusIfWorthIt(take.state)
                RulesCard(take, Modifier.fillMaxWidth())
            }
            Column(
                Modifier.fillMaxWidth().padding(top = CgSpace.S4).then(tracker.track(ControlSlot.Bottom)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
            ) {
                zoom(Modifier, false)
                KeyRow(take, Modifier.fillMaxWidth().padding(bottom = CgSpace.S8))
            }
        }
    }
}

/** Back, the source and the settings along the top; under them the banners, and the [status] and [rules] if asked. */
@Composable
private fun TopControls(
    take: TakeInputs,
    tracker: ControlsTracker,
    status: Boolean,
    rules: Boolean,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
        horizontalAlignment = horizontalAlignment,
    ) {
        TakeTopRow(take, back = true, tracker.track(ControlSlot.Top))
        Banners(take.camera, take.saveError)
        if (status) TakeStatusIfWorthIt(take.state)
        if (rules) RulesCard(take, Modifier.fillMaxWidth())
    }
}

/** The key centred, "simulate shot" to its right, where there is one. */
@Composable
private fun KeyRow(take: TakeInputs, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        RecordKey(take)
        // The take's time is the status chip's, one clock: here only "simulate shot".
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (take.canSimulate) SimulatePill(take) }
    }
}

/**
 * Follows where the floating controls are ([ControlRects]) and passes on what of the window they leave free when it
 * changes. Plain fields, not snapshot state: the controls move on every layout pass.
 */
private class ControlsTracker {
    private val rects = ControlRects()
    private var placement = ControlsPlacement.PhoneUpright
    private var onClear: (Rect) -> Unit = {}

    fun update(placement: ControlsPlacement, onClear: (Rect) -> Unit) {
        this.placement = placement
        this.onClear = onClear
    }

    fun report() {
        rects.changed(placement)?.let(onClear)
    }

    /** Records where the modified element is as [slot], on every layout pass. */
    fun track(slot: ControlSlot): Modifier = Modifier.onGloballyPositioned {
        rects[slot] = it.boundsInRoot()
        report()
    }
}

/** How far the controls stand in from the window's safe edges: further on a tablet. */
private fun edgeFor(wide: Boolean): Dp = if (wide) CgSpace.S24 else CgSpace.S12

/** The shade behind the record key on its side, so it reads over a bright picture. */
private val SHADE = CgColors.Surface.Scrim.copy(alpha = 0.55f)
private val SIDE_SHADE = 200.dp

/** Upright, the shade under the lenses and the key. */
private val FOOT_SHADE = 240.dp

/** The rules strip at most: on a tablet it does not run across the whole picture. */
private val RULES_WIDTH = 480.dp
