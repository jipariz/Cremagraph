package app.onlyshots.sources.fake

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.model.summary
import app.onlyshots.sources.BrewEvent
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.ShotSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Replays recorded shots as if a machine were pulling them, for UI work and auto-start tests without a machine. The
 * source clock starts at [clockOffsetUs] so tests catch code that confuses source time with shot time.
 */
class FakeShotSource(
    private val shots: List<Shot> = listOf(SampleShots.zer0),
    private val clockOffsetUs: Long = 1_000_000_000L,
) : ShotSource {
    override val kind = shots.first().source
    override val capabilities: Capabilities = Capabilities.of(kind)

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _live = MutableSharedFlow<app.onlyshots.core.model.Sample>(extraBufferCapacity = 64)
    override val live: SharedFlow<app.onlyshots.core.model.Sample> = _live.asSharedFlow()

    private val _brew = MutableSharedFlow<BrewEvent>(extraBufferCapacity = 8)
    override val brewEvents: SharedFlow<BrewEvent> = _brew.asSharedFlow()

    fun setConnection(state: ConnectionState) {
        _connection.value = state
    }

    /** Pulls [shot] in real time (virtual time under `runTest`): brew start, samples, brew end. */
    suspend fun pull(shot: Shot = shots.first()) {
        var clock = 0L
        _brew.emit(BrewEvent.Started(clockOffsetUs))
        for (s in shot.samples) {
            delay((s.tUs - clock) / 1000)
            clock = s.tUs
            _live.emit(s.copy(tUs = clockOffsetUs + s.tUs))
        }
        delay((shot.durationUs - clock).coerceAtLeast(0L) / 1000)
        _brew.emit(BrewEvent.Ended(clockOffsetUs + shot.durationUs))
    }

    override suspend fun history(limit: Int): List<ShotSummary> = shots.take(limit).map { it.summary() }

    override suspend fun loadShot(id: String): Shot = shots.first { it.id == id }
}
