package app.onlyshots.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.AddRow
import app.onlyshots.core.designsystem.component.Arriving
import app.onlyshots.core.designsystem.component.ConnectScaleCard
import app.onlyshots.core.designsystem.component.HairlineStack
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.designsystem.component.MachineRow
import app.onlyshots.core.designsystem.component.ScaleAction
import app.onlyshots.core.designsystem.component.ScaleDeviceRow
import app.onlyshots.core.designsystem.component.ScaleSkeletonBars
import app.onlyshots.core.designsystem.component.SectionHeader
import app.onlyshots.core.designsystem.component.SkeletonRow
import app.onlyshots.core.designsystem.component.rememberListSettled
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_add_by_address
import app.onlyshots.core.resources.home_add_by_address_subtitle
import app.onlyshots.core.resources.home_bluetooth_scale
import app.onlyshots.core.resources.home_connect_scale_subtitle
import app.onlyshots.core.resources.home_connect_scale_title
import app.onlyshots.core.resources.home_machines_title
import app.onlyshots.core.resources.home_scale_detail
import app.onlyshots.core.resources.home_scales_title
import app.onlyshots.core.resources.home_title
import app.onlyshots.core.resources.home_turn_on_bluetooth
import app.onlyshots.core.resources.text
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.machine.FoundMachine
import org.jetbrains.compose.resources.stringResource

/*
 * Where Home's shot comes from: the machines and the scales, shared by the phone's column and a wide Home's first page.
 */

/** The same header as the phones' "compose a video": back to Start and the flow's name. Help is on Start. */
@Composable
internal fun HomeHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    HeaderBack(
        stringResource(Res.string.home_title),
        onBack = onBack,
        modifier = modifier.padding(vertical = CgSpace.S8),
    )
}

/**
 * The machines, then the scales: one under the other, or [sideBySide] as two columns. [modifier] sizes, scrolls and
 * pads the list.
 */
@Composable
internal fun Sources(
    state: HomeUiState,
    onMachine: (FoundMachine) -> Unit,
    onScale: (FoundBleDevice) -> Unit,
    onAddByAddress: () -> Unit,
    onAllowBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
    sideBySide: Boolean = false,
) {
    if (sideBySide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(SOURCES_COLUMN_GAP)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                Machines(state, onMachine, onAddByAddress)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                Scales(state, onScale, onAllowBluetooth)
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
            Machines(state, onMachine, onAddByAddress)
            Scales(state, onScale, onAllowBluetooth)
        }
    }
}

@Composable
private fun Machines(state: HomeUiState, onMachine: (FoundMachine) -> Unit, onAddByAddress: () -> Unit) {
    val rows = machineRows(state)
    val note = machinesNote(state.machines.size)
    SectionHeader(
        stringResource(Res.string.home_machines_title),
        trailing = note.note?.text(),
        trailingColor = note.tone.color(),
        searching = note.searching,
    )
    // Machines turn up one by one as the search finds them: each slides open rather than pushing the list down. They
    // stack as one grid, meeting on single hairlines.
    val settled = rememberListSettled()
    if (rows.isNotEmpty()) {
        HairlineStack {
            for (row in rows) {
                key(row.kind, row.selected, row.found?.host ?: row.detail) {
                    Arriving(settled) {
                        MachineRow(
                            name = row.name.text(),
                            detail = row.detail,
                            icon = CgIcons.Machine,
                            status = stringResource(row.status.label),
                            statusDot = dotOf(row.status),
                            selected = row.selected,
                            onClick = { row.found?.let(onMachine) },
                            enabled = row.found != null || row.selected,
                            offline = row.status == RowStatus.Offline,
                            framed = false,
                        )
                    }
                }
            }
        }
    }
    if (state.machines.isEmpty()) SkeletonRow()
    AddRow(
        stringResource(Res.string.home_add_by_address),
        onClick = onAddByAddress,
        modifier = Modifier.testTag(TestTags.HomeAddByAddress),
        subtitle = if (rows.isEmpty()) stringResource(Res.string.home_add_by_address_subtitle) else null,
    )
}

@Composable
private fun Scales(state: HomeUiState, onScale: (FoundBleDevice) -> Unit, onAllowBluetooth: () -> Unit) {
    val scale = state.savedScale
    val others = otherScales(state)
    val note = scalesNote(state)
    SectionHeader(
        stringResource(Res.string.home_scales_title),
        trailing = note.note?.text(),
        trailingColor = note.tone.color(),
        searching = note.searching,
    )
    val settled = rememberListSettled()
    if (scale != null || others.isNotEmpty()) {
        // The connected scale and the ones found as one grid, meeting on single hairlines.
        HairlineStack {
            if (scale != null) {
                val status = if (state.connectingScaleId != null) RowStatus.Connecting else statusOf(state.connection)
                MachineRow(
                    name = scale.name.text(),
                    detail = stringResource(Res.string.home_bluetooth_scale),
                    icon = CgIcons.Bluetooth,
                    status = stringResource(status.label),
                    statusDot = dotOf(status),
                    selected = true,
                    onClick = {},
                    offline = status == RowStatus.Offline,
                    framed = false,
                )
            }
            for (device in others) {
                key(device.id) {
                    Arriving(settled) {
                        ScaleDeviceRow(
                            name = device.name,
                            detail = stringResource(Res.string.home_scale_detail),
                            bars = signalBars(device.advertisement.rssi),
                            action = ScaleAction.Connect,
                            onAction = { onScale(device) },
                            framed = false,
                        )
                    }
                }
            }
        }
    }
    if (state.bluetoothDenied) {
        ConnectScaleCard(
            stringResource(Res.string.home_connect_scale_title),
            stringResource(Res.string.home_connect_scale_subtitle),
            onClick = onAllowBluetooth,
        )
    } else if (state.bluetoothOff) {
        Text(
            stringResource(Res.string.home_turn_on_bluetooth),
            style = CgTheme.type.bodySmall,
            color = CgColors.Text.Secondary,
        )
    } else if (scale == null && others.isEmpty()) {
        SkeletonRow(bars = ScaleSkeletonBars)
    }
}

private fun dotOf(status: RowStatus) =
    when (status) {
        RowStatus.Ready,
        RowStatus.Connected -> CgColors.Status.Live
        RowStatus.Connecting,
        RowStatus.Busy -> CgColors.Status.Warning
        RowStatus.Offline -> CgColors.Text.Secondary
    }

/** Between the machines' and the scales' columns when they stand side by side. */
private val SOURCES_COLUMN_GAP = 32.dp
