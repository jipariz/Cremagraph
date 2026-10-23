package app.onlyshots.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import app.onlyshots.clockLength
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.Arriving
import app.onlyshots.core.designsystem.component.HairlineStack
import app.onlyshots.core.designsystem.component.MachineRow
import app.onlyshots.core.designsystem.component.SectionHeader
import app.onlyshots.core.designsystem.component.ShotRow
import app.onlyshots.core.designsystem.component.SkeletonRow
import app.onlyshots.core.designsystem.component.rememberListSettled
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_machines_title
import app.onlyshots.core.resources.home_row_ready
import app.onlyshots.core.resources.home_shots_loading
import app.onlyshots.core.resources.home_shots_none
import app.onlyshots.core.resources.home_shots_recent
import app.onlyshots.core.resources.home_shots_same_wifi
import app.onlyshots.core.resources.text
import app.onlyshots.edit.machineColor
import app.onlyshots.minuteStamp
import app.onlyshots.sources.machine.FoundMachine
import org.jetbrains.compose.resources.stringResource

/*
 * The machine shot picker's two lists, shared by the phone's steps and the wide window's sides.
 */

/** The machines on this Wi-Fi, the chosen one framed; [rowModifier] dresses each row. */
@Composable
internal fun MachineList(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    rowModifier: @Composable (FoundMachine) -> Modifier = { Modifier },
) {
    val note = machinesNote(state.machines.size)
    SectionHeader(
        stringResource(Res.string.home_machines_title),
        trailing = note.note?.text(),
        trailingColor = note.tone.color(),
        searching = note.searching,
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
internal fun MachineShots(
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
                            subtitle = summary.startedAt.minuteStamp() + " · " + clockLength(summary.durationUs),
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

private enum class ShotsList {
    LOADING,
    NONE,
    SHOTS,
}
