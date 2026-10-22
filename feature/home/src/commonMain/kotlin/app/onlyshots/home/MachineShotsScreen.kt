package app.onlyshots.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.Arriving
import app.onlyshots.core.designsystem.component.HairlineStack
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.designsystem.component.MachineRow
import app.onlyshots.core.designsystem.component.SectionHeader
import app.onlyshots.core.designsystem.component.ShotRow
import app.onlyshots.core.designsystem.component.SkeletonRow
import app.onlyshots.core.designsystem.component.rememberListSettled
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_machines_title
import app.onlyshots.core.resources.home_row_ready
import app.onlyshots.core.resources.home_shots_loading
import app.onlyshots.core.resources.home_shots_none
import app.onlyshots.core.resources.home_shots_pick_machine
import app.onlyshots.core.resources.home_shots_recent
import app.onlyshots.core.resources.home_shots_same_wifi
import app.onlyshots.core.resources.home_shots_step_machine
import app.onlyshots.core.resources.home_shots_step_shot
import app.onlyshots.core.resources.home_shots_title
import app.onlyshots.core.resources.text
import app.onlyshots.edit.machineColor
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.layout.WidePanes
import app.onlyshots.shareBounds
import app.onlyshots.sources.machine.FoundMachine
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "From the machine": the machines on this Wi-Fi, searched for as on Home; then the chosen one's recent shots, read
 * over its API. A tap on a shot loads it and goes back to the steps. A phone takes the two one after the other; a wide
 * window shows both, the machines on the left and the chosen one's shots on the right (a page each on a book foldable).
 */
@Composable
fun MachineShotsBody(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    onShot: (ShotSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWindowLayout.current
    if (layout.kind == LayoutKind.Wide) {
        WideMachineShots(state, onMachine, onShot, onBack, modifier)
        return
    }
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

/** Both steps at once: the machines on the left, the chosen one's shots (or what to do first) on the right. */
@Composable
private fun WideMachineShots(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    onShot: (ShotSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val layout = LocalWindowLayout.current
    val title = stringResource(Res.string.home_shots_title)
    val machines: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
        ) {
            MachineList(state, onMachine)
        }
    }
    val shots: @Composable () -> Unit = {
        val shown = state.machine
        if (shown == null) {
            PickAMachine()
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
            ) {
                MachineShots(state, shown, onShot)
            }
        }
    }
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base)) {
        if (layout.fold) {
            WidePanes(
                layout,
                Modifier.fillMaxSize().safeDrawingPadding(),
                gap = false,
                main = {
                    Column(Modifier.fillMaxSize().padding(horizontal = CgSpace.S16)) {
                        HeaderBack(title, onBack = onBack, modifier = Modifier.padding(vertical = CgSpace.S8))
                        Box(Modifier.weight(1f).padding(top = CgSpace.S8)) { machines() }
                    }
                },
                panel = {
                    // Level with the machines under the left page's header.
                    Box(
                        Modifier.fillMaxSize()
                            .background(CgColors.Surface.Base)
                            .padding(horizontal = CgSpace.S16)
                            .padding(top = CgSize.Touch + CgSpace.S24, bottom = CgSpace.S16)
                    ) {
                        shots()
                    }
                },
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                // The machines take a phone column; the shots the rest. A medium window shares it out.
                val side = if (maxWidth > maxHeight) 64.dp else 40.dp
                val listWidth = minOf(MACHINES_WIDTH, (maxWidth - side * 2) * 0.45f)
                Column(Modifier.fillMaxSize().padding(horizontal = side, vertical = CgSpace.S24)) {
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
    }
}

/** Before a machine is picked, the shots' side says what to do: a dashed frame with the prompt in its middle. */
@Composable
private fun PickAMachine() {
    Box(
        Modifier.fillMaxSize().drawBehind {
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

/** The machines on this Wi-Fi, the chosen one framed; [rowModifier] dresses each row. */
@Composable
private fun MachineList(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    rowModifier: @Composable (FoundMachine) -> Modifier = { Modifier },
) {
    SectionHeader(
        stringResource(Res.string.home_machines_title),
        trailing = foundNote(state.machines.size).text(),
        trailingColor = if (state.machines.isEmpty()) CgColors.Text.Secondary else CgColors.Text.Primary,
        searching = state.machines.isEmpty(),
    )
    val settled = rememberListSettled()
    if (state.machines.isNotEmpty()) {
        // One grid: the rows meet on single hairlines.
        HairlineStack {
            for (found in state.machines) {
                key(found.host) {
                    Arriving(settled) {
                        MachineRow(
                            name = found.machine.name,
                            detail = found.label,
                            icon = CgIcons.Machine,
                            status = stringResource(Res.string.home_row_ready),
                            statusDot = CgColors.Status.Live,
                            selected = found.host == state.machine?.host,
                            onClick = { onMachine(found) },
                            modifier = rowModifier(found),
                            framed = false,
                            chevron = true,
                        )
                    }
                }
            }
        }
    }
    if (state.machines.isEmpty()) SkeletonRow()
    Text(
        stringResource(Res.string.home_shots_same_wifi),
        style = CgTheme.type.bodySmall,
        color = CgColors.Text.Secondary,
    )
}

/** The [shown] machine's recent shots, as they load; [headerModifier] dresses their header. */
@Composable
private fun MachineShots(
    state: MachineShotsUiState,
    shown: FoundMachine,
    onShot: (ShotSummary) -> Unit,
    headerModifier: Modifier = Modifier,
) {
    val shots = state.shots
    SectionHeader(
        stringResource(Res.string.home_shots_recent, shown.machine.name),
        trailing = state.busy?.text() ?: if (shots == null) stringResource(Res.string.home_shots_loading) else null,
        trailingColor = CgColors.Text.Secondary,
        searching = shots == null || state.busy != null,
        modifier = headerModifier,
    )
    // Loading, none, or the shots: one ticks over to the next.
    val list =
        when {
            shots == null -> ShotsList.LOADING
            shots.isEmpty() -> ShotsList.NONE
            else -> ShotsList.SHOTS
        }
    AnimatedContent(
        list,
        transitionSpec = { with(CgMotion) { tick() } },
        label = "shots",
    ) { now ->
        when (now) {
            ShotsList.LOADING ->
                Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) { repeat(3) { SkeletonRow() } }
            ShotsList.NONE ->
                Text(
                    state.message?.text() ?: stringResource(Res.string.home_shots_none),
                    style = CgTheme.type.body,
                    color = CgColors.Text.Secondary,
                )
            // The shots as one grid of cells meeting on single hairlines; the one being fetched is chosen.
            ShotsList.SHOTS ->
                HairlineStack {
                    for (summary in shots.orEmpty()) {
                        ShotRow(
                            title = summary.profileName,
                            subtitle =
                                summary.startedAt.toString().take(16).replace('T', ' ') +
                                    " · " +
                                    length(summary.durationUs),
                            machineColor = machineColor(summary.source),
                            selected = summary.id == state.loadingId,
                            onClick = { onShot(summary) },
                            framed = false,
                        )
                    }
                }
        }
    }
    state.message
        ?.takeIf { shots?.isNotEmpty() == true }
        ?.let { Text(it.text(), style = CgTheme.type.bodySmall, color = CgColors.Status.Warning) }
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

private enum class ShotsList {
    LOADING,
    NONE,
    SHOTS,
}

/** A machine's row and the header of its shots: the same thing, one becoming the other. */
private fun machineKey(found: FoundMachine): Any = "machine-${found.host}"

private val CardShape = CgShape.Chip

/** A back header over a phone column, centred on a tablet. */
@Composable
private fun Page(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            HeaderBack(title, onBack = onBack, modifier = Modifier.padding(vertical = CgSpace.S8))
            content()
        }
    }
}

/** m:ss. */
private fun length(us: Long): String {
    val seconds = us / 1_000_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/**
 * The machine shot picker on its own, opened from the editor's shot sheet: the same machines and recent shots as "edit
 * a video"'s, and the loaded shot goes to [onPicked].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MachineShotsScreen(
    onPicked: (Shot) -> Unit,
    onBack: () -> Unit,
    viewModel: MachineShotsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.openMachines() }
    LaunchedEffect(state.shot) {
        state.shot?.let {
            viewModel.opened()
            onPicked(it)
        }
    }
    // From a machine's shots back to the machines; from the machines back to the editor.
    val back = { if (state.machine != null) viewModel.back() else onBack() }
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None)) { back() }
    MachineShotsBody(
        state = state,
        onMachine = viewModel::chooseMachine,
        onShot = viewModel::chooseShot,
        onBack = back,
    )
}

/** The wide layout's machines column at most: a phone column. */
private val MACHINES_WIDTH = 480.dp
