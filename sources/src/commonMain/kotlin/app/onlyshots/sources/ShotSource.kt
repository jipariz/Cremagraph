package app.onlyshots.sources

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotHistory
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.SourceKind
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionState {
    data object Connecting : ConnectionState

    data object Connected : ConnectionState

    data class Unreachable(val reason: String) : ConnectionState

    /** The machine refused the socket because too many clients are connected. */
    data object Busy : ConnectionState {
        const val MESSAGE = "the machine is busy: close another web interface"
    }
}

/** Brew start and end on the source clock (the clock of [ShotSource.live] sample times). */
sealed interface BrewEvent {
    val atUs: Long

    data class Started(override val atUs: Long) : BrewEvent

    data class Ended(override val atUs: Long) : BrewEvent
}

/**
 * One machine or scale. Screens only ever see [capabilities], never the machine itself.
 *
 * In [live], [Sample.tUs] is on the source clock, the same clock as [BrewEvent.atUs]; the recorder turns it into shot
 * time (see [LiveShotAssembler]).
 */
interface ShotSource : ShotHistory {
    val kind: SourceKind
    val capabilities: Capabilities
    val connection: StateFlow<ConnectionState>
    val live: SharedFlow<Sample>
    val brewEvents: SharedFlow<BrewEvent>

    override suspend fun history(limit: Int): List<ShotSummary>

    override suspend fun loadShot(id: String): Shot

    /** The record screen is ready for a shot: a scale tares and starts its zero over. Machines need nothing. */
    suspend fun prepareForShot() {}
}
