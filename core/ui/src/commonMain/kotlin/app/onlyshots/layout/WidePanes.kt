package app.onlyshots.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.component.TwoPane
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The wide layout of a screen: [main] (the picture) and a [panel] (everything about it), the panel beside it
 * ([PanePlacement.Side], [panelWidth] wide) or under it across the window ([PanePlacement.Below], [belowHeight] tall),
 * as [placePanel] decides for what the picture shows. On a fold ([WindowLayout.fold]) the two are a page each, meeting
 * at it, whatever [placement] says.
 *
 * The two slots move between placements and sizes animated when [animateOn] has changed since the last layout (the
 * format, picked by hand), and at once otherwise (the window turned or resized), so only a choice is animated.
 */
@Composable
fun WidePanes(
    layout: WindowLayout,
    modifier: Modifier = Modifier,
    placement: PanePlacement = PanePlacement.Side,
    /** The side panel's width. By default [defaultPanelWidthDp]; a picture beside it asks for [sidePanelWidthDp]. */
    panelWidth: Dp? = null,
    belowHeight: Dp = 0.dp,
    /**
     * The directive's gap between the panes; off where the picture should meet the panel (the record screen's camera).
     * A hinge between them is still kept clear.
     */
    gap: Boolean = true,
    animateOn: Any? = null,
    main: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    if (layout.fold) {
        FoldPanes(layout, modifier, first = main) {
            Box(Modifier.fillMaxSize().background(CgColors.Surface.Bar)) { panel() }
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val (mainTarget, panelTarget) =
            with(density) {
                if (placement == PanePlacement.Below) {
                    val panelH = belowHeight.toPx().coerceAtMost(h)
                    Rect(0f, 0f, w, h - panelH) to Rect(0f, h - panelH, w, h)
                } else {
                    val spacer = if (gap) layout.directive.horizontalPartitionSpacerSize.toPx() else 0f
                    val panelW =
                        (panelWidth?.toPx() ?: defaultPanelWidthDp(w.toDp().value, spacer.toDp().value).dp.toPx())
                            .coerceAtMost(w)
                    Rect(0f, 0f, (w - panelW - spacer).coerceAtLeast(0f), h) to Rect(w - panelW, 0f, w, h)
                }
            }
        val mainRect = remember { Animatable(mainTarget, Rect.VectorConverter) }
        val panelRect = remember { Animatable(panelTarget, Rect.VectorConverter) }
        val motion = remember { KeyedMotion(animateOn) }
        LaunchedEffect(mainTarget, panelTarget, animateOn) {
            motion.move(
                animateOn,
                animate = {
                    coroutineScope {
                        launch { mainRect.animateTo(mainTarget, CgMotion.layout()) }
                        launch { panelRect.animateTo(panelTarget, CgMotion.layout()) }
                    }
                },
                snap = {
                    mainRect.snapTo(mainTarget)
                    panelRect.snapTo(panelTarget)
                },
            )
        }
        Slot(mainRect.value) { main() }
        Slot(panelRect.value, Modifier.background(CgColors.Surface.Bar)) { panel() }
    }
}

/** A box at [rect], in px inside the panes. */
@Composable
private fun Slot(rect: Rect, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .then(modifier)
    ) {
        content()
    }
}

/**
 * A fold's two pages, [first] and [second], meeting at it ([WindowLayout.fold]). [layout] measures the split from the
 * window's edge; the panes measure from where they are placed, which a caller's safe-area padding (or none) moves, so
 * the split is taken from where they land.
 */
@Composable
fun FoldPanes(
    layout: WindowLayout,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var startPx by remember { mutableFloatStateOf(0f) }
    TwoPane(
        vertical = false,
        split = (layout.splitDp.dp - with(density) { startPx.toDp() }).coerceAtLeast(0.dp),
        hinge = layout.hingeDp.dp,
        modifier = modifier.onPlaced { startPx = it.positionInWindow().x },
        first = first,
        second = second,
    )
}
