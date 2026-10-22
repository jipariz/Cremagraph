package app.onlyshots.sources

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.fake.FakeShotSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The machine the app is currently using, behind one [ShotSource] that never changes identity. Screens and view models
 * keep the same instance while the user connects, disconnects or switches machines underneath.
 *
 * It also owns the connection: [use] remembers how to build a source, and each connection runs in a child scope that
 * [pause] cancels. Cancelling that scope is the only way to stop [keepConnected]'s retry loop (and the holder's own,
 * building a source that could not be built), so the holder and the lifetime belong together.
 *
 * ponytail: [use], [pause] and [resume] are called from the UI thread only; add a mutex if something off-main ever
 * drives them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceHolder(
    private val scope: CoroutineScope,
    private val initial: ShotSource,
    /** How long after a failed build (a scale not found, say) it is tried again. */
    private val retryMs: Long = RETRY_INTERVAL_MS,
) : ShotSource {
    private val _current = MutableStateFlow(initial)
    val current: StateFlow<ShotSource> = _current.asStateFlow()

    /** Covers the gap while a source is being built: scanning for a scale can take seconds. */
    private val _override = MutableStateFlow<ConnectionState?>(null)

    private var factory: (suspend (CoroutineScope) -> ShotSource?)? = null
    private var connectionScope: CoroutineScope? = null
    private var building: Job? = null

    override val kind: SourceKind
        get() = _current.value.kind

    override val capabilities: Capabilities
        get() = _current.value.capabilities

    override val connection: StateFlow<ConnectionState> =
        combine(_override, _current.flatMapLatest { it.connection }) { override, state -> override ?: state }
            .stateIn(scope, SharingStarted.Eagerly, initial.connection.value)

    // Eagerly, not WhileSubscribed: a sample or a brew event between two subscribers must not be
    // dropped, and RecordSession subscribes from its constructor anyway.
    override val live: SharedFlow<Sample> =
        _current.flatMapLatest { it.live }.shareIn(scope, SharingStarted.Eagerly, replay = 0)

    override val brewEvents: SharedFlow<BrewEvent> =
        _current.flatMapLatest { it.brewEvents }.shareIn(scope, SharingStarted.Eagerly, replay = 0)

    /**
     * The connected machine's recent shots, for the editor's "from the machine". The replayed sample is no machine, and
     * a machine that is not reached has nothing to list: both give none.
     */
    override suspend fun history(limit: Int): List<ShotSummary> {
        val source = _current.value
        if (source is FakeShotSource || source.connection.value != ConnectionState.Connected) return emptyList()
        return source.history(limit)
    }

    override suspend fun loadShot(id: String): Shot = _current.value.loadShot(id)

    override suspend fun prepareForShot() = _current.value.prepareForShot()

    /** Remembers how to build the chosen source and connects it now. A chosen source ends the sample. */
    fun use(factory: suspend (CoroutineScope) -> ShotSource?) {
        sampling = false
        beforeSample = null
        this.factory = factory
        resume()
    }

    /** Whether the sample shot is the source ("try a sample shot"), until [endSample]. */
    var sampling = false
        private set

    /** What the sample stood in for: put back when it ends. */
    private var beforeSample: (suspend (CoroutineScope) -> ShotSource?)? = null

    /**
     * The replayed sample shot ([sample]) as the source, for one take: the only way to a simulated shot. The source it
     * replaces comes back with [endSample].
     */
    fun useSample(sample: suspend (CoroutineScope) -> ShotSource?) {
        val before = if (sampling) beforeSample else factory
        use(sample)
        sampling = true
        beforeSample = before
    }

    /**
     * The sample is over: the source it stood in for is set up again, or none. Until (unless) that one connects, the
     * app is on the disconnected source it starts on, never on the sample.
     */
    fun endSample() {
        if (!sampling) return
        val back = beforeSample
        useInitial()
        back?.let(::use)
    }

    /** Reconnects whatever [use] last set up; nothing happens if no machine is configured. */
    fun resume() {
        val factory = factory ?: return
        stopConnection()
        val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        connectionScope = child
        _override.value = ConnectionState.Connecting
        // Built again every [retryMs] until it is: a scale that was off when the app came up is found once it is on,
        // as a machine's own loop reconnects it. Stops with the connection scope (pause, another source).
        building = child.launch {
            while (true) {
                val source =
                    try {
                        factory(child)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _override.value = ConnectionState.Unreachable(e.message ?: "could not connect")
                        null
                    }
                if (source != null) {
                    _current.value = source
                    _override.value = null
                    return@launch
                }
                if (_override.value !is ConnectionState.Unreachable) {
                    _override.value = ConnectionState.Unreachable("could not find it")
                }
                delay(retryMs)
            }
        }
    }

    /** Back to the source the app starts on with nothing set up, forgetting whatever [use] was given. */
    fun useInitial() {
        sampling = false
        beforeSample = null
        factory = null
        stopConnection()
        _current.value = initial
        _override.value = null
    }

    /** Drops the connection but keeps the machine's identity, so the UI still names it. */
    fun pause() {
        stopConnection()
        _override.value = null
    }

    private fun stopConnection() {
        building = null
        connectionScope?.cancel()
        connectionScope = null
    }
}
