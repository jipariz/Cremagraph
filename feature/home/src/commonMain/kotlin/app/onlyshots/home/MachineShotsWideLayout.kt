package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_shots_pick_machine
import app.onlyshots.core.resources.home_shots_title
import app.onlyshots.layout.FoldPanes
import app.onlyshots.layout.WindowLayout
import app.onlyshots.sources.machine.FoundMachine
import org.jetbrains.compose.resources.stringResource

/**
 * Both steps at once: the machines on the left, the chosen one's shots (or what to do first) on the right; a page each
 * on a book foldable.
 */
@Composable
internal fun MachineShotsWideLayout(
    layout: WindowLayout,
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    onShot: (ShotSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.home_shots_title)
    val machines: @Composable () -> Unit = { MachinesSide(state, onMachine) }
    val shots: @Composable () -> Unit = { ShotsSide(state, onShot) }
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base)) {
        if (layout.fold) FoldPages(layout, title, onBack, machines, shots)
        else SideBySide(layout.landscape, title, onBack, machines, shots)
    }
}

/** A book foldable: the header and the machines on the left page, the shots on the right. */
@Composable
private fun FoldPages(
    layout: WindowLayout,
    title: String,
    onBack: () -> Unit,
    machines: @Composable () -> Unit,
    shots: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldPanes(
        layout,
        modifier.fillMaxSize().safeDrawingPadding(),
        first = {
            Column(Modifier.fillMaxSize().padding(horizontal = CgSpace.S16)) {
                HeaderBack(title, onBack = onBack, modifier = Modifier.padding(vertical = CgSpace.S8))
                Box(Modifier.weight(1f).padding(top = CgSpace.S8)) { machines() }
            }
        },
    ) {
        // Level with the machines under the left page's header.
        Box(
            Modifier.fillMaxSize()
                .background(CgColors.Surface.Base)
                .padding(horizontal = CgSpace.S16)
                .padding(top = CgSize.Touch + CgSpace.S24, bottom = CgSpace.S16)
        ) {
            shots()
        }
    }
}

/** One window: the header over the machines in a phone column (or a medium window's share) and the shots beside. */
@Composable
private fun SideBySide(
    landscape: Boolean,
    title: String,
    onBack: () -> Unit,
    machines: @Composable () -> Unit,
    shots: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = if (landscape) LANDSCAPE_MARGIN else UPRIGHT_MARGIN
    // Measured for fit: the machines' share of the row.
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        val listWidth = machinesWidthDp((maxWidth - margin * 2).value).dp
        Column(Modifier.fillMaxSize().padding(horizontal = margin, vertical = CgSpace.S24)) {
            HeaderBack(title, onBack = onBack, modifier = Modifier.padding(bottom = CgSpace.S16))
            Row(Modifier.weight(1f).fillMaxWidth().padding(top = CgSpace.S16)) {
                Box(Modifier.width(listWidth).fillMaxHeight()) { machines() }
                Box(
                    Modifier.padding(horizontal = CgSpace.S24)
                        .width(CgStroke.Hairline)
                        .fillMaxHeight()
                        .background(CgColors.Line.Hairline)
                )
                Box(Modifier.weight(1f).fillMaxHeight()) { shots() }
            }
        }
    }
}

@Composable
private fun MachinesSide(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
    ) {
        MachineList(state, onMachine)
    }
}

@Composable
private fun ShotsSide(state: MachineShotsUiState, onShot: (ShotSummary) -> Unit, modifier: Modifier = Modifier) {
    val shown = state.machine
    if (shown == null) {
        PickAMachine(modifier)
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
        ) {
            MachineShots(state, shown, onShot)
        }
    }
}

/** Before a machine is picked, the shots' side says what to do: a dashed frame with the prompt in its middle. */
@Composable
private fun PickAMachine(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().drawBehind {
            val w = CgStroke.Hairline.toPx()
            drawRect(
                CgColors.Line.Hairline,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * w, 4f * w))),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.home_shots_pick_machine).caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(CgSpace.S24),
        )
    }
}

/** The side margins of a wide window on its side, and upright. */
private val LANDSCAPE_MARGIN = 64.dp
private val UPRIGHT_MARGIN = 40.dp
