package app.onlyshots.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_shots_step_machine
import app.onlyshots.core.resources.home_shots_step_shot
import app.onlyshots.core.resources.home_shots_title
import app.onlyshots.edit.machineColor
import app.onlyshots.shareBounds
import app.onlyshots.sources.machine.FoundMachine
import org.jetbrains.compose.resources.stringResource

/** A phone's picker: the two steps one after the other, the machine's row becoming the header of its shots. */
@Composable
internal fun MachineShotsPhoneLayout(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    onShot: (ShotSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val machine = state.machine
    Page(title = stringResource(Res.string.home_shots_title), onBack = onBack, modifier = modifier) {
        Steps(machine, Modifier.padding(horizontal = CgSpace.S16).padding(bottom = CgSpace.S8))
        // Picking a machine moves on to its shots as a page pushed in place: the machine's row becomes the header of
        // its shots, and back from them it returns to its row.
        AnimatedContent(
            machine,
            Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = initialState == null
                slideInHorizontally(CgMotion.long()) { if (forward) it else -it } togetherWith
                    slideOutHorizontally(CgMotion.long()) { if (forward) -it else it }
            },
            contentKey = { it?.host },
            label = "machine",
        ) { shown ->
            val page = this
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = CgSpace.S16),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
            ) {
                if (shown == null) {
                    MachineList(state, onMachine) { Modifier.shareBounds(machineKey(it), page, CardShape) }
                } else {
                    MachineShots(state, shown, onShot, Modifier.shareBounds(machineKey(shown), page, CardShape))
                }
            }
        }
    }
}

/**
 * Where the phone's two steps are: 01 MACHINE lit while one is picked; then the machine's name (with its colour) and 02
 * SHOT lit.
 */
@Composable
private fun Steps(machine: FoundMachine?, modifier: Modifier = Modifier) {
    val first = machine?.machine?.name?.caps() ?: ("01 " + stringResource(Res.string.home_shots_step_machine).caps())
    val second = "02 " + stringResource(Res.string.home_shots_step_shot).caps()
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).border(CgStroke.Hairline, CgColors.Line.Hairline)) {
        Step(first, lit = machine == null, bar = machine?.let { machineColor(it.machine.kind) })
        Box(Modifier.width(CgStroke.Hairline).fillMaxHeight().background(CgColors.Line.Hairline))
        Step(second, lit = machine != null)
    }
}

@Composable
private fun RowScope.Step(text: String, lit: Boolean, bar: Color? = null) {
    Row(
        Modifier.weight(1f)
            .fillMaxHeight()
            .background(if (lit) CgColors.Brand.Bone else Color.Transparent)
            .padding(horizontal = CgSpace.S12, vertical = CgSpace.S8),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bar != null) Box(Modifier.size(width = CgSpace.S4, height = CgSpace.S12).background(bar))
        Text(
            text,
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp),
            color = if (lit) CgColors.Text.OnLight else CgColors.Text.Secondary,
        )
    }
}

/** A back header over a phone column, centred on a tablet. */
@Composable
private fun Page(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = PHONE_COLUMN_WIDTH).fillMaxSize()) {
            HeaderBack(title, onBack = onBack, modifier = Modifier.padding(vertical = CgSpace.S8))
            content()
        }
    }
}

/** A machine's row and the header of its shots: the same thing, one becoming the other. */
private fun machineKey(found: FoundMachine): Any = "machine-${found.host}"

private val CardShape = CgShape.Chip
