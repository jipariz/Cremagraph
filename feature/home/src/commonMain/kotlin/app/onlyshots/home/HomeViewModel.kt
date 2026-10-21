package app.onlyshots.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.onlyshots.connect.factoryFor
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.machine.MachineLog
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.SourceHolder
import app.onlyshots.sources.ble.BleScan
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.fake.FakeShotSource
import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.NetworkMachines
import app.onlyshots.sources.machine.NetworkScan
import app.onlyshots.storage.AppSettings
import app.onlyshots.storage.AppSettingsRepo
import io.ktor.client.HttpClient
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Figma "Home": the app starts from what the user has. While Home is in front it keeps searching the Wi-Fi for machines
 * and Bluetooth for scales, lists whatever turns up, and the user picks one. The pick is saved and handed to the holder
 * every screen reads from. One source at a time: picking a scale replaces the machine, and the other way round.
 */
class HomeViewModel(
    private val holder: SourceHolder,
    private val settings: AppSettingsRepo,
    private val client: HttpClient,
    private val networkScan: NetworkScan,
    private val scanScales: () -> Flow<FoundBleDevice> = { BleScan.devices() },
    private val pauseBetweenSearchesMs: Long = PAUSE_BETWEEN_SEARCHES_MS,
) : ViewModel() {
    private val local = MutableStateFlow(HomeUiState(saved = savedSource(settings.value)))

    val state: StateFlow<HomeUiState> =
        combine(local, holder.connection) { s, connection -> s.copy(connection = connection) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    /**
     * The next take, for a wide Home's second pane: the recording settings, and the source's newest reading. The
     * readings are only listened to while someone collects this (Home in front).
     */
    val take: StateFlow<NextTake> =
        combine(settings.state.map { it.recording }, holder.live.map<Sample, Sample?> { it }.onStart { emit(null) }) {
                recording,
                live ->
                NextTake(recording, live)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NextTake(settings.value.recording))

    /** The recording settings from Home: the same ones the record screen edits. */
    fun updateRecording(change: (RecordingSettings) -> RecordingSettings) = settings.update {
        it.copy(recording = change(it.recording))
    }

    private var machineSearch: Job? = null
    private var scaleSearch: Job? = null
    private var bluetoothAllowed = false

    /** Whether Bluetooth has been asked for since Home was first shown; it is asked once, as Home opens. */
    var askedForBluetooth = false
        private set

    init {
        viewModelScope.launch {
            holder.connection.collect { c ->
                if (c == ConnectionState.Connected || c is ConnectionState.Unreachable) {
                    local.update { it.copy(connectingScaleId = null) }
                }
            }
        }
    }

    /** Home is in front: search until [stopSearching]. */
    fun startSearching() {
        if (machineSearch == null) machineSearch = viewModelScope.launch { searchMachines() }
        if (bluetoothAllowed) startScaleSearch()
    }

    /** Home is no longer in front: nothing keeps probing the network or the radio behind other screens. */
    fun stopSearching() {
        machineSearch?.cancel()
        machineSearch = null
        scaleSearch?.cancel()
        scaleSearch = null
    }

    fun onBluetoothAnswer(granted: Boolean) {
        askedForBluetooth = true
        bluetoothAllowed = granted
        local.update { it.copy(bluetoothDenied = !granted) }
        if (granted && machineSearch != null) startScaleSearch()
    }

    /**
     * Search after search, with a pause between. A machine shows up as soon as it answers and stays until a whole
     * search goes by without it, so a slow answer never makes the list flicker.
     */
    private suspend fun searchMachines() {
        while (viewModelScope.isActive) {
            val seen = mutableListOf<FoundMachine>()
            // The machine in use is not probed: it is there, and a probe would take one of its few sockets.
            val connected =
                local.value.savedMachine?.host?.takeIf { holder.connection.value == ConnectionState.Connected }
            networkScan.machines(trusted = setOfNotNull(connected)).collect { found ->
                seen += found
                local.update { s ->
                    // Same machine at the same IP: updated in place, so a name learnt later does not add a row.
                    val i = s.machines.indexOfFirst { it.sameMachineAs(found) }
                    s.copy(
                        machines = if (i < 0) s.machines + found else s.machines.toMutableList().also { it[i] = found }
                    )
                }
            }
            local.update { it.copy(machines = it.machines.filter { m -> seen.any { m.sameMachineAs(it) } }) }
            delay(pauseBetweenSearchesMs)
        }
    }

    private fun startScaleSearch() {
        if (scaleSearch != null) return
        // The scan throws when Bluetooth is off or missing (always, on the iOS simulator). That is a state to show,
        // not a crash: say so and try again, so turning Bluetooth on is picked up without leaving Home.
        scaleSearch = viewModelScope.launch {
            while (isActive) {
                try {
                    scanScales().collect { device ->
                        local.update { s ->
                            val scales = if (s.scales.any { it.id == device.id }) s.scales else s.scales + device
                            s.copy(scales = scales, bluetoothOff = false)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    local.update { it.copy(bluetoothOff = true, scales = emptyList()) }
                }
                delay(pauseBetweenSearchesMs)
            }
        }
    }

    /** Tapping a machine chooses and connects it. */
    fun connectTo(found: FoundMachine) = use(found.machine.kind, found.address)

    /**
     * Try the app without a machine: the recorded Zer0 shot becomes the source for the record screen it opens, until
     * its take is saved or Home is back in front ([endSample]). Nothing is saved, so the next launch starts as before.
     */
    fun useSample() = holder.useSample { FakeShotSource() }

    /**
     * Back from the sample's record screen (or Home left altogether), or recording without data: the sample is over,
     * and the source that was there is put back, the saved machine or scale, else none, so the record screen opened
     * next has what Home's button says and never the simulated shot.
     */
    fun endSample() = holder.endSample()

    override fun onCleared() = endSample()

    fun connectScale(device: FoundBleDevice) {
        val factory = factoryFor(SourceKind.SCALE, "", device.id, client) ?: return
        settings.update { it.copy(source = SourceKind.SCALE, scaleId = device.id, scaleName = device.name) }
        local.update { it.copy(saved = savedSource(settings.value), connectingScaleId = device.id) }
        holder.use(factory)
    }

    fun openAddress() = local.update { it.copy(addressSheet = true) }

    fun closeAddress() = local.update { it.copy(addressSheet = false) }

    fun setAddress(text: String) = local.update { it.copy(address = text) }

    /** A machine the search cannot see, by IP or name. Only Gaggiuino exists, so the address is one. */
    fun addByAddress() {
        if (use(NetworkMachines.all.first().kind, local.value.address.trim())) {
            local.update { it.copy(addressSheet = false) }
        }
    }

    private fun use(kind: SourceKind, address: String): Boolean {
        val factory = factoryFor(kind, address, null, client) ?: return false
        settings.update { it.copy(source = kind, host = address) }
        local.update { it.copy(saved = savedSource(settings.value)) }
        holder.use(factory)
        return true
    }

    private fun FoundMachine.sameMachineAs(other: FoundMachine) =
        machine.kind == other.machine.kind && host == other.host && port == other.port

    companion object {
        const val PAUSE_BETWEEN_SEARCHES_MS = 5_000L

        /** A source saved by an older version that this one no longer supports reads as nothing saved. */
        private fun savedSource(s: AppSettings): SavedSource? =
            s.source
                ?.takeIf { it in MachineLog.kinds }
                ?.let { SavedSource(it, s.host, s.scaleId, s.scaleName) }
                ?.takeUnless { it.isScale && it.scaleId == null }
    }
}
