package app.onlyshots.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.Arriving
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
import app.onlyshots.core.resources.home_shots_recent
import app.onlyshots.core.resources.home_shots_same_wifi
import app.onlyshots.core.resources.home_shots_title
import app.onlyshots.core.resources.text
import app.onlyshots.edit.machineColor
import app.onlyshots.shareBounds
import app.onlyshots.sources.machine.FoundMachine
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "From the machine": the machines on this Wi-Fi, searched for as on Home; then the chosen one's recent shots, read
 * over its API. A tap on a shot loads it and goes back to the steps.
 */
@Composable
fun MachineShotsBody(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    onShot: (ShotSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val machine = state.machine
    Page(title = stringResource(Res.string.home_shots_title), onBack = onBack, modifier = modifier) {
        // Picking a machine moves on to its shots as a page pushed in place: the machine's row becomes the header of
        // its shots, and back from them it returns to its row.
        AnimatedContent(
            machine,
            Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = initialState == null
                (slideInHorizontally(CgMotion.offsetSpec) { if (forward) it / 6 else -it / 6 } +
                    fadeIn(CgMotion.medium())) togetherWith
                    (slideOutHorizontally(CgMotion.offsetSpec) { if (forward) -it / 6 else it / 6 } +
                        fadeOut(CgMotion.short()))
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
                    SectionHeader(
                        stringResource(Res.string.home_machines_title),
                        trailing = foundNote(state.machines.size).text(),
                        trailingColor = if (state.machines.isEmpty()) CgColors.Brand.Signal else CgColors.Text.Primary,
                        searching = state.machines.isEmpty(),
                    )
                    val settled = rememberListSettled()
                    for (found in state.machines) {
                        key(found.host) {
                            Arriving(settled) {
                                MachineRow(
                                    name = found.machine.name,
                                    detail = found.label,
                                    tileColor = machineColor(found.machine.kind),
                                    status = stringResource(Res.string.home_row_ready),
                                    statusDot = CgColors.Status.Live,
                                    selected = false,
                                    onClick = { onMachine(found) },
                                    modifier = Modifier.shareBounds(machineKey(found), page, CardShape),
                                )
                            }
                        }
                    }
                    if (state.machines.isEmpty()) SkeletonRow()
                    Text(
                        stringResource(Res.string.home_shots_same_wifi),
                        style = CgTheme.type.bodySmall,
                        color = CgColors.Text.Secondary,
                    )
                } else {
                    val shots = state.shots
                    SectionHeader(
                        stringResource(Res.string.home_shots_recent, shown.machine.name),
                        trailing =
                            state.busy?.text()
                                ?: if (shots == null) stringResource(Res.string.home_shots_loading) else null,
                        trailingColor = CgColors.Brand.Signal,
                        searching = shots == null || state.busy != null,
                        modifier = Modifier.shareBounds(machineKey(shown), page, CardShape),
                    )
                    // Loading, none, or the shots: one fades into the next.
                    val list =
                        when {
                            shots == null -> ShotsList.LOADING
                            shots.isEmpty() -> ShotsList.NONE
                            else -> ShotsList.SHOTS
                        }
                    AnimatedContent(
                        list,
                        transitionSpec = {
                            fadeIn(CgMotion.medium()) togetherWith
                                fadeOut(CgMotion.short()) using
                                SizeTransform { _, _ -> CgMotion.sizeSpec }
                        },
                        label = "shots",
                    ) { now ->
                        // The shots as one list of cells meeting on their hairlines.
                        Column {
                            when (now) {
                                ShotsList.LOADING -> repeat(3) { SkeletonRow() }
                                ShotsList.NONE ->
                                    Text(
                                        state.message?.text() ?: stringResource(Res.string.home_shots_none),
                                        style = CgTheme.type.body,
                                        color = CgColors.Text.Secondary,
                                    )
                                ShotsList.SHOTS ->
                                    for (summary in shots.orEmpty()) {
                                        ShotRow(
                                            title = summary.profileName,
                                            subtitle =
                                                summary.startedAt.toString().take(16).replace('T', ' ') +
                                                    " · " +
                                                    length(summary.durationUs),
                                            machineColor = machineColor(summary.source),
                                            onClick = { onShot(summary) },
                                        )
                                    }
                            }
                        }
                    }
                    state.message
                        ?.takeIf { shots?.isNotEmpty() == true }
                        ?.let { Text(it.text(), style = CgTheme.type.bodySmall, color = CgColors.Status.Warning) }
                }
            }
        }
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
    BackHandler { back() }
    MachineShotsBody(
        state = state,
        onMachine = viewModel::chooseMachine,
        onShot = viewModel::chooseShot,
        onBack = back,
    )
}
