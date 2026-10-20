package app.onlyshots.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.home_shots_load_failed
import app.onlyshots.core.resources.home_shots_loading_shot
import app.onlyshots.core.resources.home_shots_no_answer
import app.onlyshots.core.resources.text
import app.onlyshots.machine.MachineLog
import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.NetworkMachines
import app.onlyshots.sources.machine.NetworkScan
import app.onlyshots.storage.AppSettingsRepo
import io.ktor.client.HttpClient
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MachineShotsUiState(
    /** The shot loaded whole, to hand back; null until one is picked. */
    val shot: Shot? = null,
    val machines: List<FoundMachine> = emptyList(),
    /** The machine whose shots are listed; null while one is being chosen. */
    val machine: FoundMachine? = null,
    /** Its recent shots; null while they load. */
    val shots: List<ShotSummary>? = null,
    /** "loading the shot…" while one is fetched whole. */
    val busy: UiText? = null,
    val message: UiText? = null,
)

/**
 * The machine shot picker (phones and desktop): the machines on the Wi-Fi, searched for as Home does, then one
 * machine's recent shots over its HTTP API, and the picked one loaded whole. Nothing connects live: the editor only
 * needs the shot.
 */
class MachineShotsViewModel(
    private val client: HttpClient,
    private val networkScan: NetworkScan,
    private val settings: AppSettingsRepo,
    private val pauseBetweenSearchesMs: Long = HomeViewModel.PAUSE_BETWEEN_SEARCHES_MS,
) : ViewModel() {
    val state: StateFlow<MachineShotsUiState>
        field = MutableStateFlow(MachineShotsUiState())

    private var search: Job? = null
    private var loading: Job? = null

    fun openMachines() {
        state.update { it.copy(machine = null, shots = null, message = null) }
        startSearching()
    }

    /** Back from the machine pane: from a machine's shots to the machines, from the machines to the steps. */
    fun back() {
        loading?.cancel()
        val s = state.value
        if (s.machine != null) state.update { it.copy(machine = null, shots = null, busy = null, message = null) }
        else closeMachines()
    }

    fun closeMachines() {
        stopSearching()
        loading?.cancel()
        state.update { it.copy(machine = null, shots = null, busy = null) }
    }

    fun chooseMachine(found: FoundMachine) {
        state.update { it.copy(machine = found, shots = null, message = null) }
        loading?.cancel()
        loading = viewModelScope.launch {
            val shots = fetch { found.machine.history(client, found.host, found.port).list(RECENT) }
            state.update {
                it.copy(
                    shots = shots.orEmpty(),
                    message = if (shots == null) Res.string.home_shots_no_answer.text(found.machine.name) else null,
                )
            }
        }
    }

    fun chooseShot(summary: ShotSummary) {
        val found = state.value.machine ?: return
        state.update { it.copy(busy = Res.string.home_shots_loading_shot.text(), message = null) }
        loading?.cancel()
        loading = viewModelScope.launch {
            val shot = fetch { found.machine.history(client, found.host, found.port).load(summary.id) }
            if (shot == null) {
                state.update { it.copy(busy = null, message = Res.string.home_shots_load_failed.text()) }
                return@launch
            }
            state.update { it.copy(shot = shot, busy = null) }
            closeMachines()
        }
    }

    fun clearMessage() = state.update { it.copy(message = null) }

    /** The picked shot was handed back: the next visit starts with nothing picked. */
    fun opened() = state.update { MachineShotsUiState() }

    /**
     * Search after search while the machine pane is open, as Home does. The machine saved on Home is listed first,
     * before any search answers.
     */
    private fun startSearching() {
        if (search != null) return
        val saved = savedMachine()
        state.update { it.copy(machines = listOfNotNull(saved)) }
        search = viewModelScope.launch {
            while (isActive) {
                val seen = mutableListOf<FoundMachine>()
                networkScan.machines().collect { found ->
                    seen += found
                    state.update { s -> s.copy(machines = withMachine(s.machines, found)) }
                }
                state.update { s ->
                    s.copy(machines = s.machines.filter { m -> m == saved || seen.any { it.sameAs(m) } })
                }
                delay(pauseBetweenSearchesMs)
            }
        }
    }

    private fun stopSearching() {
        search?.cancel()
        search = null
    }

    private fun savedMachine(): FoundMachine? {
        val s = settings.value
        val machine = NetworkMachines.of(s.source ?: return null) ?: return null
        val address = s.host?.takeIf { it.isNotBlank() } ?: MachineLog.defaultAddress(machine.kind)
        val (host, port) = MachineLog.parseAddress(address, machine.defaultPort) ?: return null
        return FoundMachine(machine, host, port)
    }

    private fun withMachine(list: List<FoundMachine>, found: FoundMachine): List<FoundMachine> {
        val i = list.indexOfFirst { it.sameAs(found) }
        return if (i < 0) list + found else list.toMutableList().also { it[i] = found }
    }

    private fun FoundMachine.sameAs(other: FoundMachine) =
        machine.kind == other.machine.kind && port == other.port && (host == other.host || isAt(other.host))

    private suspend fun <T> fetch(block: suspend () -> T): T? =
        try {
            withContext(Dispatchers.Default) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    override fun onCleared() {
        stopSearching()
    }

    private companion object {
        const val RECENT = 10
    }
}
