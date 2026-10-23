package app.onlyshots.record

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion.tick
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.TwoPane
import app.onlyshots.layout.WindowLayout

/**
 * Half folded: the camera ([pane], with its own lenses and status) in the upper half, the controls on the lower one. A
 * book foldable resting on its side gets [TabletopPanel]; a flip in flex mode [FlexPanel]. [zoom] is the lenses' strip.
 */
@Composable
internal fun RecordStackedLayout(
    layout: WindowLayout,
    take: TakeInputs,
    pane: @Composable (Modifier, PaneChrome) -> Unit,
    zoom: @Composable (Modifier, vertical: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().safeDrawingPadding()) {
        TwoPane(
            vertical = true,
            split = layout.splitDp.dp,
            hinge = layout.hingeDp.dp,
            first = { pane(Modifier.fillMaxSize(), PaneChrome.Own) },
            second = {
                Box(Modifier.fillMaxSize().background(CgColors.Surface.Bar)) {
                    if (layout.onItsSide) TabletopPanel(take, zoom = { m -> zoom(m, false) }) else FlexPanel(take)
                }
            },
        )
    }
}

/**
 * A book foldable resting on its side: under the camera, the source along the top; then the status, the rules and the
 * readings that matter before a shot on the left, and beside them, past a hairline, the lenses, the clock and the
 * button. [zoom] is the lenses' strip, where the camera has more than one.
 */
@Composable
private fun TabletopPanel(
    take: TakeInputs,
    modifier: Modifier = Modifier,
    zoom: @Composable (Modifier) -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(start = CgSpace.S24, end = CgSpace.S24, top = CgSpace.S12, bottom = CgSpace.S16),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        TakeTopRow(take, back = true)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CgSpace.S24)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
            ) {
                TakeStatusIfWorthIt(take.state)
                RulesCard(take)
                // Recording, every reading; before, the two that matter, one ticking over into the other.
                AnimatedContent(
                    take.state.recording,
                    transitionSpec = { tick() },
                    label = "tabletop",
                ) { recording ->
                    if (recording) ReadingsGrid(take, columns = 4, large = false)
                    else ReadingsGrid(take, columns = 2, large = true, only = BEFORE_A_SHOT)
                }
                Banners(take.camera, take.saveError)
            }
            Column(
                Modifier.width(TABLETOP_SIDE)
                    .fillMaxHeight()
                    .drawBehind { drawRect(CgColors.Line.Hairline, size = Size(CgStroke.Hairline.toPx(), size.height)) }
                    .padding(start = CgSpace.S24),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S16, Alignment.Bottom),
            ) {
                zoom(Modifier)
                TakeFooter(take, clock = CgTheme.type.pivot)
            }
        }
    }
}

/**
 * A flip in flex mode: the camera stands in the upper half, and the lower half is the controls. Back and the source,
 * the readings as large cells, the rules, and under a hairline the clock, large, with the button.
 */
@Composable
private fun FlexPanel(take: TakeInputs, modifier: Modifier = Modifier) {
    PanelColumn(modifier, footer = take, clock = CgTheme.type.valueLarge) {
        TakeTopRow(take, back = true)
        ReadingsGrid(take, columns = 2, large = true)
        RulesCard(take)
        Banners(take.camera, take.saveError)
    }
}

/** The readings that matter before a shot, on a tabletop's panel: the water's heat and the cup's weight. */
private val BEFORE_A_SHOT = setOf(Quantity.Temperature, Quantity.Weight)

/** The tabletop panel's trailing column: the lenses, the clock and the key. */
private val TABLETOP_SIDE = 400.dp
