package app.onlyshots.home

import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.model.oneDecimal
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.home_action_offline
import app.onlyshots.core.resources.home_action_open_camera
import app.onlyshots.core.resources.home_action_pick_source
import app.onlyshots.core.resources.home_action_sample
import app.onlyshots.core.resources.home_action_sample_caption
import app.onlyshots.core.resources.home_action_scale_caption
import app.onlyshots.core.resources.home_action_without_data
import app.onlyshots.core.resources.home_auto_start_brew_switch
import app.onlyshots.core.resources.home_found
import app.onlyshots.core.resources.home_row_busy
import app.onlyshots.core.resources.home_row_connected
import app.onlyshots.core.resources.home_row_connecting
import app.onlyshots.core.resources.home_row_offline
import app.onlyshots.core.resources.home_row_ready
import app.onlyshots.core.resources.home_rule_start_now
import app.onlyshots.core.resources.home_rule_start_now_scale
import app.onlyshots.core.resources.home_rule_start_pre_roll
import app.onlyshots.core.resources.home_rule_start_pre_roll_scale
import app.onlyshots.core.resources.home_rule_stop_fixed
import app.onlyshots.core.resources.home_rule_stop_manual
import app.onlyshots.core.resources.home_rule_stop_settles
import app.onlyshots.core.resources.home_rule_stop_tail
import app.onlyshots.core.resources.home_searching
import app.onlyshots.core.resources.home_status_brewing
import app.onlyshots.core.resources.home_status_busy
import app.onlyshots.core.resources.home_status_connecting
import app.onlyshots.core.resources.home_status_idle
import app.onlyshots.core.resources.home_status_no_source
import app.onlyshots.core.resources.home_status_offline
import app.onlyshots.core.resources.home_status_sample
import app.onlyshots.core.resources.home_status_scale_loaded
import app.onlyshots.core.resources.home_status_scale_settled
import app.onlyshots.core.resources.home_status_waiting
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.reading_flow
import app.onlyshots.core.resources.reading_pressure
import app.onlyshots.core.resources.reading_temperature
import app.onlyshots.core.resources.reading_weight
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.unit_bar
import app.onlyshots.core.resources.unit_celsius
import app.onlyshots.core.resources.unit_g_per_s
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.core.resources.unit_ml_per_s
import app.onlyshots.machine.MachineLog
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.machine.AutoStart
import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.NetworkMachines
import org.jetbrains.compose.resources.StringResource

/** The source saved as the one to use: a machine at [host], or the scale [scaleId]. */
data class SavedSource(val kind: SourceKind, val host: String?, val scaleId: String?, val scaleName: String?) {
    val isScale: Boolean
        get() = kind == SourceKind.SCALE

    val name: UiText
        get() = if (isScale) scaleName?.raw() ?: Res.string.source_scale.text() else MachineLog.label(kind)
}

data class HomeUiState(
    /** Machines on the network: everything found by the last full search, plus what the running one has added. */
    val machines: List<FoundMachine> = emptyList(),
    /** Scales seen by the Bluetooth scan that runs while Home is open. */
    val scales: List<FoundBleDevice> = emptyList(),
    /** Bluetooth was refused, so scales can only be looked for after asking again. */
    val bluetoothDenied: Boolean = false,
    /** Bluetooth is switched off, or the device has none: the scan failed and is retried. */
    val bluetoothOff: Boolean = false,
    val saved: SavedSource? = null,
    val connection: ConnectionState? = null,
    val connectingScaleId: String? = null,
    val addressSheet: Boolean = false,
    val address: String = MachineLog.defaultAddress(NetworkMachines.all.first().kind),
    /**
     * The sample shot is the source for now: a recorded Zer0 shot replayed on the record screen, so the app can be
     * tried (and reviewed) without a machine. Never saved; picking a machine or a scale ends it.
     */
    val sample: Boolean = false,
) {
    val savedMachine: SavedSource?
        get() = saved?.takeUnless { it.isScale }

    val savedScale: SavedSource?
        get() = saved?.takeIf { it.isScale }
}

enum class RowStatus(val label: StringResource) {
    Ready(Res.string.home_row_ready),
    Connected(Res.string.home_row_connected),
    Connecting(Res.string.home_row_connecting),
    Busy(Res.string.home_row_busy),
    Offline(Res.string.home_row_offline),
}

/** One machine on Home: the saved one first and selected, then the others the search found. */
data class MachineRowModel(
    val kind: SourceKind,
    val name: UiText,
    val detail: String,
    val status: RowStatus,
    val selected: Boolean,
    val found: FoundMachine?,
)

fun machineRows(s: HomeUiState): List<MachineRowModel> {
    val saved = s.savedMachine
    val savedHost = saved?.let { it.host ?: MachineLog.defaultAddress(it.kind) }
    // The saved machine may be saved by name and found by IP, or the other way round: one machine, one row.
    fun isSaved(f: FoundMachine) = saved != null && f.machine.kind == saved.kind && f.isAt(savedHost!!)
    val current = saved?.let { m ->
        val found = s.machines.firstOrNull(::isSaved)
        MachineRowModel(
            kind = m.kind,
            name = m.name,
            detail = found?.label ?: savedHost!!,
            status = statusOf(s.connection),
            selected = true,
            found = found,
        )
    }
    val others =
        s.machines.filterNot(::isSaved).map {
            MachineRowModel(it.machine.kind, it.machine.name.raw(), it.label, RowStatus.Ready, false, it)
        }
    return listOfNotNull(current) + others
}

/** The scales to offer: every one nearby except the saved scale, strongest signal first. */
fun otherScales(s: HomeUiState): List<FoundBleDevice> =
    s.scales.filterNot { it.id == s.savedScale?.scaleId }.sortedByDescending { it.advertisement.rssi }

fun statusOf(connection: ConnectionState?): RowStatus =
    when (connection) {
        ConnectionState.Connected -> RowStatus.Connected
        ConnectionState.Busy -> RowStatus.Busy
        is ConnectionState.Unreachable -> RowStatus.Offline
        ConnectionState.Connecting,
        null -> RowStatus.Connecting
    }

/** A section's note on the right: how many were found, or that the search is still on before anything was. */
fun foundNote(count: Int): UiText =
    if (count == 0) Res.string.home_searching.text() else Res.string.home_found.text(count.toString())

/** The record action and the line under it (Figma "Primary action"). */
data class RecordActionModel(val text: UiText, val caption: UiText, val enabled: Boolean)

/**
 * With nothing chosen, or the chosen source offline, there is no graph to wait for: the action records without data,
 * and turns back into "open camera · …" as soon as the source answers.
 */
fun recordAction(s: HomeUiState): RecordActionModel {
    val saved = s.saved
    return when {
        s.sample ->
            RecordActionModel(
                Res.string.home_action_sample.text(),
                Res.string.home_action_sample_caption.text(),
                enabled = true,
            )
        saved == null ->
            RecordActionModel(
                Res.string.home_action_without_data.text(),
                Res.string.home_action_pick_source.text(),
                enabled = true,
            )
        s.connection is ConnectionState.Unreachable ->
            RecordActionModel(
                Res.string.home_action_without_data.text(),
                Res.string.home_action_offline.text(saved.name),
                enabled = true,
            )
        saved.isScale ->
            RecordActionModel(
                Res.string.home_action_open_camera.text(saved.name),
                Res.string.home_action_scale_caption.text(),
                enabled = true,
            )
        else ->
            RecordActionModel(
                Res.string.home_action_open_camera.text(saved.name),
                NetworkMachines.of(saved.kind)?.autoStart?.let { autoStartHint(it).text() } ?: "".raw(),
                enabled = true,
            )
    }
}

/** How a recording starts on its own with a machine, under the record action. */
fun autoStartHint(autoStart: AutoStart): StringResource =
    when (autoStart) {
        AutoStart.BREW_SWITCH -> Res.string.home_auto_start_brew_switch
    }

/** Four bars from an advertisement's signal strength. */
fun signalBars(rssi: Int): Int =
    when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }

/** The scale brands named on Home; the full list is in the README. */
const val SCALE_BRANDS = "Acaia · Bookoo · Decent Scale · Felicita · Timemore · Varia"

/** What a wide Home's second pane knows about the next take: the recording settings, and the newest reading. */
data class NextTake(val settings: RecordingSettings = RecordingSettings(), val live: Sample? = null)

/** Which quantity a reading tile shows, so the screen can colour it as the graph does. */
enum class ReadingKind {
    Temperature,
    Pressure,
    Weight,
    Flow,
    WeightFlow,
}

/** One of the "Next take" tiles: a quantity, its value now ("–" before the first reading) and its unit. */
data class TakeReading(
    val label: StringResource,
    val value: String,
    val unit: StringResource,
    val kind: ReadingKind,
)

/** The line over the tiles: whether readings are coming in, and what the machine is doing. */
fun takeStatus(s: HomeUiState, live: Sample?): UiText {
    if (s.sample) return Res.string.home_status_sample.text()
    val saved = s.saved ?: return Res.string.home_status_no_source.text()
    return when (s.connection) {
        ConnectionState.Connected ->
            when {
                live == null -> Res.string.home_status_waiting.text()
                saved.isScale ->
                    if ((live.weight ?: 0f) > 0.5f) Res.string.home_status_scale_loaded.text()
                    else Res.string.home_status_scale_settled.text()
                live.brewing -> Res.string.home_status_brewing.text()
                else -> Res.string.home_status_idle.text()
            }
        is ConnectionState.Unreachable -> Res.string.home_status_offline.text(saved.name)
        ConnectionState.Busy -> Res.string.home_status_busy.text()
        ConnectionState.Connecting,
        null -> Res.string.home_status_connecting.text()
    }
}

/**
 * The three tiles under the status: a machine's temperature, pressure and weight (its flow where it has no weight), a
 * scale's weight and how fast it climbs. Nothing without a source.
 */
fun takeReadings(s: HomeUiState, live: Sample?): List<TakeReading> {
    if (s.sample) return machineReadings(live)
    val saved = s.saved ?: return emptyList()
    fun v(x: Float?) = x?.oneDecimal() ?: "–"
    return if (saved.isScale)
        listOf(
            TakeReading(Res.string.reading_weight, v(live?.weight), Res.string.unit_grams, ReadingKind.Weight),
            TakeReading(Res.string.reading_flow, v(live?.weightFlow), Res.string.unit_g_per_s, ReadingKind.WeightFlow),
        )
    else machineReadings(live)
}

private fun machineReadings(live: Sample?): List<TakeReading> {
    fun v(x: Float?) = x?.oneDecimal() ?: "–"
    return listOf(
        TakeReading(
            Res.string.reading_temperature,
            v(live?.temperature),
            Res.string.unit_celsius,
            ReadingKind.Temperature,
        ),
        TakeReading(Res.string.reading_pressure, v(live?.pressure), Res.string.unit_bar, ReadingKind.Pressure),
        if (live?.weight != null || live?.flow == null)
            TakeReading(Res.string.reading_weight, v(live?.weight), Res.string.unit_grams, ReadingKind.Weight)
        else TakeReading(Res.string.reading_flow, v(live.flow), Res.string.unit_ml_per_s, ReadingKind.Flow),
    )
}

/**
 * "Start with the shot" and what it keeps, for the rule's switch. A scale knows no pump: its shot starts with the first
 * drops landing.
 */
fun startRuleDetail(settings: RecordingSettings, kind: SourceKind?): UiText {
    val scale = kind == SourceKind.SCALE
    return when {
        settings.preRollSeconds == 0 && scale -> Res.string.home_rule_start_now_scale.text()
        settings.preRollSeconds == 0 -> Res.string.home_rule_start_now.text()
        scale -> Res.string.home_rule_start_pre_roll_scale.text(settings.preRollSeconds.toString())
        else -> Res.string.home_rule_start_pre_roll.text(settings.preRollSeconds.toString())
    }
}

/** "Stop after the shot" and when, for the rule's switch. */
fun stopRuleDetail(settings: RecordingSettings, kind: SourceKind?): UiText =
    when (val rule = kind?.let { settings.effectiveFor(it).stopRule } ?: settings.stopRule) {
        is StopRule.ShotEndPlusTail -> Res.string.home_rule_stop_tail.text(rule.tailSeconds.toString())
        is StopRule.WeightSettles -> Res.string.home_rule_stop_settles.text(rule.seconds.toString())
        is StopRule.Fixed -> Res.string.home_rule_stop_fixed.text(rule.seconds.toString())
        StopRule.Manual -> Res.string.home_rule_stop_manual.text()
    }

/** The stop rule's switch: off is by hand, on brings back the usual rule. */
fun RecordingSettings.withStopAfterShot(on: Boolean): RecordingSettings =
    copy(stopRule = if (on) StopRule.ShotEndPlusTail() else StopRule.Manual)
