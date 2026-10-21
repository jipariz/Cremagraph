package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.PRIVACY_URL
import app.onlyshots.SUPPORT_URL
import app.onlyshots.StopRuleChoice
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.AddRow
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.designsystem.component.CgStepper
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.designsystem.component.ConnectScaleCard
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.MachineRow
import app.onlyshots.core.designsystem.component.ReadingTile
import app.onlyshots.core.designsystem.component.RecordAction
import app.onlyshots.core.designsystem.component.RuleRow
import app.onlyshots.core.designsystem.component.ScaleAction
import app.onlyshots.core.designsystem.component.ScaleDeviceRow
import app.onlyshots.core.designsystem.component.SectionHeader
import app.onlyshots.core.designsystem.component.SegmentedControl
import app.onlyshots.core.designsystem.component.SettingRow
import app.onlyshots.core.designsystem.component.SkeletonRow
import app.onlyshots.core.model.AppVersion
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.model.availableFor
import app.onlyshots.core.model.stepLabel
import app.onlyshots.core.model.stepped
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_version
import app.onlyshots.core.resources.home_about
import app.onlyshots.core.resources.home_about_text
import app.onlyshots.core.resources.home_add_by_address
import app.onlyshots.core.resources.home_add_by_address_subtitle
import app.onlyshots.core.resources.home_address_label
import app.onlyshots.core.resources.home_address_note
import app.onlyshots.core.resources.home_bluetooth_off
import app.onlyshots.core.resources.home_bluetooth_scale
import app.onlyshots.core.resources.home_connect
import app.onlyshots.core.resources.home_connect_scale_subtitle
import app.onlyshots.core.resources.home_connect_scale_title
import app.onlyshots.core.resources.home_help_machines
import app.onlyshots.core.resources.home_help_machines_note
import app.onlyshots.core.resources.home_help_scales_note
import app.onlyshots.core.resources.home_machines_title
import app.onlyshots.core.resources.home_next_take
import app.onlyshots.core.resources.home_privacy
import app.onlyshots.core.resources.home_record
import app.onlyshots.core.resources.home_record_armed_auto
import app.onlyshots.core.resources.home_record_armed_manual
import app.onlyshots.core.resources.home_rule_pre_roll
import app.onlyshots.core.resources.home_rule_start
import app.onlyshots.core.resources.home_rule_stop
import app.onlyshots.core.resources.home_scale_detail
import app.onlyshots.core.resources.home_scales_title
import app.onlyshots.core.resources.home_support
import app.onlyshots.core.resources.home_take_camera_only
import app.onlyshots.core.resources.home_take_machine
import app.onlyshots.core.resources.home_take_sample
import app.onlyshots.core.resources.home_title
import app.onlyshots.core.resources.home_try_sample
import app.onlyshots.core.resources.home_turn_on_bluetooth
import app.onlyshots.core.resources.record_pre_roll_hint
import app.onlyshots.core.resources.record_pre_roll_hint_scale
import app.onlyshots.core.resources.record_seconds
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.text
import app.onlyshots.edit.machineColor
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.layout.WidePanes
import app.onlyshots.platform.rememberBluetoothPermission
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.NetworkMachines
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onTrySample: () -> Unit = onOpenCamera,
    onBack: () -> Unit = {},
    onPrivacy: (() -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val take by viewModel.take.collectAsStateWithLifecycle()
    var help by remember { mutableStateOf(false) }
    val askBluetooth = rememberBluetoothPermission(viewModel::onBluetoothAnswer)
    // Scales are searched for all the time, so Bluetooth is asked for as Home first opens; already allowed, this
    // answers at once without a prompt. Refused, the "connect a scale" card asks again.
    LaunchedEffect(Unit) { if (!viewModel.askedForBluetooth) askBluetooth() }
    LifecycleStartEffect(viewModel) {
        viewModel.startSearching()
        onStopOrDispose { viewModel.stopSearching() }
    }
    HomeBody(
        state = state,
        onMachine = viewModel::connectTo,
        onScale = viewModel::connectScale,
        onAddByAddress = viewModel::openAddress,
        onAllowBluetooth = askBluetooth,
        onOpenCamera = onOpenCamera,
        // The sample needs nothing to be picked: the camera opens and the shot plays as soon as it is armed.
        onSample = {
            viewModel.useSample()
            onTrySample()
        },
        onHelp = { help = true },
        onBack = onBack,
        take = take,
        onRecording = viewModel::updateRecording,
    )
    if (help) {
        CgSheet(title = stringResource(Res.string.home_about), onDismiss = { help = false }) {
            HelpContent(
                onPrivacy =
                    onPrivacy?.let { open ->
                        {
                            help = false
                            open()
                        }
                    }
            )
        }
    }
    if (state.addressSheet) {
        AddressSheet(
            address = state.address,
            onAddress = viewModel::setAddress,
            onConnect = viewModel::addByAddress,
            onDismiss = viewModel::closeAddress,
        )
    }
}

/** Figma "Home" without the view model, so every state can be drawn from a plain [HomeUiState]. */
@Composable
fun HomeBody(
    state: HomeUiState,
    onMachine: (FoundMachine) -> Unit,
    onScale: (FoundBleDevice) -> Unit,
    onAddByAddress: () -> Unit,
    onAllowBluetooth: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
    take: NextTake = NextTake(),
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit = {},
    onSample: () -> Unit = {},
    onHelp: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val layout = LocalWindowLayout.current
    if (layout.kind == LayoutKind.Wide) {
        // Two pages: where the shot comes from, and the take the record button starts.
        Box(modifier.fillMaxSize().background(CgColors.Surface.Base)) {
            WidePanes(
                layout,
                Modifier.fillMaxSize().safeDrawingPadding(),
                main = {
                    Column(Modifier.fillMaxSize()) {
                        Header(onBack, onHelp)
                        Column(
                            Modifier.weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = CgSpace.S24,
                                    end = CgSpace.S24,
                                    top = CgSpace.S6,
                                    bottom = CgSpace.S24,
                                ),
                            verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
                        ) {
                            Machines(state, onMachine, onAddByAddress)
                            Scales(state, onScale, onAllowBluetooth)
                        }
                    }
                },
                panel = { NextTakePane(state, take, onRecording, onOpenCamera, onSample) },
            )
        }
        return
    }
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        // The design is a phone column; on a tablet it stays one, centred.
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            Header(onBack, onHelp)
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = CgSpace.S16, end = CgSpace.S16, top = CgSpace.S6, bottom = CgSpace.S16),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
            ) {
                Machines(state, onMachine, onAddByAddress)
                Scales(state, onScale, onAllowBluetooth)
            }
            val action = recordAction(state)
            Column(
                Modifier.padding(start = CgSpace.S16, end = CgSpace.S16, top = CgSpace.S12, bottom = CgSpace.S8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RecordAction(
                    text = action.text.text(),
                    caption = action.caption.text(),
                    enabled = action.enabled,
                    onClick = onOpenCamera,
                )
                SampleLink(state, onSample)
            }
        }
    }
}

@Composable
/** The same header as the phones' "edit a video": back to Start, the flow's name, and the ? sheet. */
private fun Header(onBack: () -> Unit, onHelp: () -> Unit) {
    HeaderBack(
        stringResource(Res.string.home_title),
        onBack = onBack,
        modifier = Modifier.padding(vertical = CgSpace.S8),
        action = { IconCircleButton(CgIcons.Help, stringResource(Res.string.home_about), onClick = onHelp) },
    )
}

@Composable
private fun Machines(state: HomeUiState, onMachine: (FoundMachine) -> Unit, onAddByAddress: () -> Unit) {
    val rows = machineRows(state)
    SectionHeader(
        stringResource(Res.string.home_machines_title),
        trailing = foundNote(state.machines.size).text(),
        trailingColor = if (state.machines.isEmpty()) CgColors.Status.Highlight else CgColors.Text.Dim,
        searching = state.machines.isEmpty(),
    )
    for (row in rows) {
        MachineRow(
            name = row.name.text(),
            detail = row.detail,
            tileColor = machineColor(row.kind),
            status = stringResource(row.status.label),
            statusDot = dotOf(row.status),
            selected = row.selected,
            onClick = { row.found?.let(onMachine) },
            enabled = row.found != null || row.selected,
        )
    }
    if (state.machines.isEmpty()) SkeletonRow()
    AddRow(
        stringResource(Res.string.home_add_by_address),
        onClick = onAddByAddress,
        subtitle = if (rows.isEmpty()) stringResource(Res.string.home_add_by_address_subtitle) else null,
    )
}

@Composable
private fun Scales(state: HomeUiState, onScale: (FoundBleDevice) -> Unit, onAllowBluetooth: () -> Unit) {
    val scale = state.savedScale
    val others = otherScales(state)
    SectionHeader(
        stringResource(Res.string.home_scales_title),
        trailing =
            when {
                state.bluetoothDenied -> null
                state.bluetoothOff -> stringResource(Res.string.home_bluetooth_off)
                else -> foundNote(state.scales.size).text()
            },
        trailingColor =
            if (state.scales.isEmpty() && !state.bluetoothOff) CgColors.Status.Highlight else CgColors.Text.Dim,
        searching = state.scales.isEmpty() && !state.bluetoothOff && !state.bluetoothDenied,
    )
    if (scale != null) {
        val status = if (state.connectingScaleId != null) RowStatus.Connecting else statusOf(state.connection)
        MachineRow(
            name = scale.name.text(),
            detail = stringResource(Res.string.home_bluetooth_scale),
            tileColor = CgColors.Status.Info,
            status = stringResource(status.label),
            statusDot = dotOf(status),
            selected = true,
            onClick = {},
        )
    }
    for (device in others) {
        ScaleDeviceRow(
            name = device.name,
            detail = stringResource(Res.string.home_scale_detail),
            bars = signalBars(device.advertisement.rssi),
            action = ScaleAction.Connect,
            onAction = { onScale(device) },
        )
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
            style = CgTheme.type.caption,
            color = CgColors.Text.Secondary,
        )
    } else if (scale == null && others.isEmpty()) {
        SkeletonRow()
    }
}

private fun dotOf(status: RowStatus) =
    when (status) {
        RowStatus.Ready,
        RowStatus.Connected -> CgColors.Status.Live
        RowStatus.Connecting,
        RowStatus.Busy -> CgColors.Status.Highlight
        RowStatus.Offline -> CgColors.Text.Dim
    }

@Composable
private fun AddressSheet(address: String, onAddress: (String) -> Unit, onConnect: () -> Unit, onDismiss: () -> Unit) {
    CgSheet(title = stringResource(Res.string.home_add_by_address), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
            Text(
                stringResource(Res.string.home_address_note),
                style = CgTheme.type.caption,
                color = CgColors.Text.Secondary,
            )
            CgTextField(
                value = address,
                onValueChange = onAddress,
                label = stringResource(Res.string.home_address_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
                modifier = Modifier.fillMaxWidth(),
            )
            CgButton(stringResource(Res.string.home_connect), onClick = onConnect, enabled = address.isNotBlank())
        }
    }
}

/**
 * A wide Home's second page: the take the record button starts. The source and its live readings, how the take starts
 * and stops (the record screen's own settings), and the record button at the bottom.
 */
@Composable
private fun NextTakePane(
    state: HomeUiState,
    take: NextTake,
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit,
    onOpenCamera: () -> Unit,
    onSample: () -> Unit,
) {
    val settings = take.settings
    // A short panel (a tablet on its side) tightens up, so the rules still show above the record button.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_PANEL_HEIGHT
        val gap = if (compact) CgSpace.S12 else 20.dp
        Column(
            Modifier.fillMaxSize().padding(if (compact) CgSpace.S16 else CgSpace.S24),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
                    SectionLabel(stringResource(Res.string.home_next_take))
                    Text(
                        buildAnnotatedString {
                            append(
                                if (state.sample) stringResource(Res.string.home_take_sample)
                                else state.saved?.name?.text() ?: stringResource(Res.string.home_take_camera_only)
                            )
                            state.saved?.let {
                                withStyle(SpanStyle(color = CgColors.Text.Dim)) {
                                    append(" · ")
                                    append(
                                        stringResource(
                                            if (it.isScale) Res.string.source_scale else Res.string.home_take_machine
                                        )
                                    )
                                }
                            }
                        },
                        style = CgTheme.type.sheetTitle,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val live = state.connection == ConnectionState.Connected && take.live != null
                        Box(
                            Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(if (live) CgColors.Status.Live else CgColors.Text.Dim)
                        )
                        Text(
                            takeStatus(state, take.live).text(),
                            style = CgTheme.type.caption,
                            color = CgColors.Text.Secondary,
                        )
                    }
                    val readings = takeReadings(state, take.live)
                    if (readings.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                            for (r in readings) {
                                ReadingTile(
                                    stringResource(r.label),
                                    r.value,
                                    stringResource(r.unit),
                                    Modifier.weight(1f),
                                    color = r.kind.color(),
                                )
                            }
                        }
                    }
                }
                TakeRules(settings, state.saved?.kind, onRecording)
            }
            val action = recordAction(state)
            val withoutData = state.saved == null || state.connection is ConnectionState.Unreachable
            RecordAction(
                text = if (withoutData) action.text.text() else stringResource(Res.string.home_record),
                caption =
                    if (withoutData) action.caption.text()
                    else if (settings.autoStart) stringResource(Res.string.home_record_armed_auto)
                    else stringResource(Res.string.home_record_armed_manual),
                enabled = action.enabled,
                onClick = onOpenCamera,
            )
            SampleLink(state, onSample, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/**
 * How the next take starts and stops, all of it, as the record screen's settings have it: the switches, the pre-roll,
 * and while the take stops by itself, on what and after how long. Worded for the [kind] of source: a scale has no pump.
 */
@Composable
private fun TakeRules(
    settings: RecordingSettings,
    kind: SourceKind?,
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit,
) {
    val scale = kind == SourceKind.SCALE
    // What this source really runs with: a scale shows "weight settles" where a machine's "shot end" is saved.
    val effective = settings.effective(kind)
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        RuleRow(
            stringResource(Res.string.home_rule_start),
            startRuleDetail(settings, kind).text(),
            checked = settings.autoStart,
            onToggle = { on -> onRecording { it.copy(autoStart = on) } },
        )
        SettingRow(
            stringResource(Res.string.home_rule_pre_roll),
            hint =
                stringResource(if (scale) Res.string.record_pre_roll_hint_scale else Res.string.record_pre_roll_hint),
        ) {
            CgStepper(
                value = stringResource(Res.string.record_seconds, settings.preRollSeconds.toString()),
                onMinus = { onRecording { it.copy(preRollSeconds = (it.preRollSeconds - 1).coerceIn(PRE_ROLL)) } },
                onPlus = { onRecording { it.copy(preRollSeconds = (it.preRollSeconds + 1).coerceIn(PRE_ROLL)) } },
                minusEnabled = settings.preRollSeconds > PRE_ROLL.first,
                plusEnabled = settings.preRollSeconds < PRE_ROLL.last,
            )
        }
        RuleRow(
            stringResource(Res.string.home_rule_stop),
            stopRuleDetail(settings, kind).text(),
            checked = settings.stopRule != StopRule.Manual,
            onToggle = { on -> onRecording { it.withStopAfterShot(on) } },
        )
        if (effective.stopRule != StopRule.Manual) {
            // "By hand" is the switch above; the picker holds the rules that stop by themselves.
            val choices =
                StopRuleChoice.entries.filter {
                    it != StopRuleChoice.MANUAL && (kind == null || it.rule().availableFor(kind))
                }
            val current = StopRuleChoice.of(effective.stopRule)
            SegmentedControl(
                fill = true,
                options = choices.map { stringResource(it.label) },
                selectedIndex = choices.indexOf(current),
                onSelect = { i -> onRecording { it.copy(stopRule = choices[i].rule()) } },
            )
            val label = effective.stopRule.stepLabel
            val parameter = current.parameter
            if (label != null && parameter != null) {
                SettingRow(stringResource(parameter).replaceFirstChar { it.uppercase() }) {
                    // Stepped from what is shown, so a scale's stand-in "weight settles" becomes the saved rule.
                    CgStepper(
                        value = label,
                        onMinus = { onRecording { s -> s.copy(stopRule = s.effective(kind).stopRule.stepped(-1)) } },
                        onPlus = { onRecording { s -> s.copy(stopRule = s.effective(kind).stopRule.stepped(1)) } },
                    )
                }
            }
        }
    }
}

private fun RecordingSettings.effective(kind: SourceKind?) = kind?.let { effectiveFor(it) } ?: this

private val PRE_ROLL = RecordingSettings.PRE_ROLL_RANGE

/** Under this the next take's panel is short, and tighter. */
private val COMPACT_PANEL_HEIGHT = 640.dp

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary)
}

/** The colour the graph draws this quantity in, so a tile reads as its line. */
private fun ReadingKind.color(): Color =
    when (this) {
        ReadingKind.Temperature -> CgColors.Series.Temperature
        ReadingKind.Pressure -> CgColors.Series.Pressure
        ReadingKind.Weight -> CgColors.Text.Primary
        ReadingKind.Flow -> CgColors.Series.Flow
        ReadingKind.WeightFlow -> CgColors.Series.WeightFlow
    }

/**
 * Under the record button: a recorded shot to try the app with when there is no machine at hand (and for App Review,
 * which has none). Gone while the sample is already the source.
 */
@Composable
private fun SampleLink(state: HomeUiState, onSample: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.sample) Box(modifier) { FooterLink(stringResource(Res.string.home_try_sample), onClick = onSample) }
}

/**
 * Behind Home's ? button: the machines and scales OnlyShots knows, how they are found, and the privacy policy and where
 * to get help (guidelines 5.1.1 and 1.5). Stateless, so the screenshot tests can draw it outside its sheet.
 */
@Composable
fun HelpContent(modifier: Modifier = Modifier, onPrivacy: (() -> Unit)? = null, version: String = AppVersion.NAME) {
    val uri = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S16)) {
        Text(stringResource(Res.string.home_about_text), style = CgTheme.type.body, color = CgColors.Text.Secondary)
        HelpSection(
            stringResource(Res.string.home_help_machines),
            NetworkMachines.all.joinToString(" · ") { it.name },
            stringResource(Res.string.home_help_machines_note),
        )
        HelpSection(
            stringResource(Res.string.home_scales_title),
            SCALE_BRANDS,
            stringResource(Res.string.home_help_scales_note),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S16)) {
            // The app's own privacy screen where the host has one; the published page otherwise.
            FooterLink(stringResource(Res.string.home_privacy)) { onPrivacy?.invoke() ?: uri.openUri(PRIVACY_URL) }
            FooterLink(stringResource(Res.string.home_support)) { uri.openUri(SUPPORT_URL) }
        }
        Text(stringResource(Res.string.core_version, version), style = CgTheme.type.caption, color = CgColors.Text.Dim)
    }
}

@Composable
private fun HelpSection(title: String, names: String, note: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
        SectionLabel(title)
        Text(names, style = CgTheme.type.body)
        Text(note, style = CgTheme.type.caption, color = CgColors.Text.Dim)
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = CgTheme.type.caption.copy(textDecoration = TextDecoration.Underline),
        color = CgColors.Text.Secondary,
        modifier =
            Modifier.heightIn(min = CgSize.Touch).wrapContentHeight().clickable(role = Role.Button, onClick = onClick),
    )
}
