package app.onlyshots.sources.ble

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.BrewEvent
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.FirstDropsDetector
import app.onlyshots.sources.ShotSource
import app.onlyshots.sources.machine.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A connected Bluetooth scale as the sources see it, independent of the BLE stack. */
interface BleDeviceLink {
    val name: String
    val protocol: BleProtocol
    val readings: SharedFlow<BleReading>
    val connection: StateFlow<ConnectionState>

    suspend fun tare()
}

/**
 * "Scale only": weight (and the scale's own flow, when it reports one) from a standalone Bluetooth scale, for machines
 * without a network API. There is no machine to say when the shot starts, so it starts when the first drops land (0.3 g
 * above where the scale settled, so the cup does not count; see [FirstDropsDetector]) and the stop rule decides the
 * end. Times are this phone's monotonic clock.
 *
 * A Bluetooth scale takes one connection at a time: while OnlyShots holds it, the scale's own app (or a machine that
 * pairs with scales) cannot use it.
 */
class ScaleShotSource(
    private val scale: BleDeviceLink,
    private val nowUs: () -> Long = MonotonicClock::nowUs,
    /** Every reading and what the trigger made of it, for debug builds. */
    private val log: ((String) -> Unit)? = null,
) : ShotSource {
    override val kind = SourceKind.SCALE
    override val capabilities = Capabilities.of(SourceKind.SCALE)
    override val connection: StateFlow<ConnectionState> = scale.connection

    private val _live = MutableSharedFlow<Sample>(extraBufferCapacity = 256)
    override val live: SharedFlow<Sample> = _live.asSharedFlow()
    private val _brew = MutableSharedFlow<BrewEvent>(extraBufferCapacity = 16)
    override val brewEvents: SharedFlow<BrewEvent> = _brew.asSharedFlow()

    private val lock = Mutex()
    private val firstDrops = FirstDropsDetector()

    fun start(scope: CoroutineScope): Job = scope.launch { scale.readings.collect { r -> onScale(r) } }

    internal suspend fun onScale(r: BleReading) {
        val w = r.weightG ?: return
        val (sample, seen) =
            lock.withLock {
                val at = nowUs()
                val seen = firstDrops.onReading(at, w)
                log?.invoke(
                    "t=${at / 1000} ms w=$w zero=${firstDrops.zero} rel=${seen.weight} " +
                        "brewing=${firstDrops.brewing}${if (seen.started != null) " START" else ""}"
                )
                Sample(tUs = at, weight = seen.weight, weightFlow = r.weightFlowGps, brewing = firstDrops.brewing) to
                    seen
            }
        seen.started?.let { _brew.emit(it) }
        // The cup came off (or the scale was tared): the shot ends at that reading.
        seen.ended?.let { _brew.emit(it) }
        _live.emit(sample)
    }

    override suspend fun prepareForShot() = tare()

    /** Tare before the shot; it also re-arms the first-drops trigger. */
    suspend fun tare() {
        log?.invoke("tare")
        scale.tare()
        lock.withLock { firstDrops.reset() }
    }

    override suspend fun history(limit: Int): List<ShotSummary> = emptyList()

    override suspend fun loadShot(id: String): Shot =
        throw NoSuchElementException("a scale has no shot history; recordings live in the library")
}
