package app.onlyshots.sources.machine

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.BrewEvent
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.ShotSource
import app.onlyshots.sources.SourceBusyException
import app.onlyshots.sources.keepConnected
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one WebSocket message from a machine means. */
sealed interface MachineMessage {
    data class Reading(val sample: Sample) : MachineMessage

    data class Brew(val event: BrewEvent) : MachineMessage

    data object Ignored : MachineMessage
}

/**
 * Machine-specific wire format. Messages carry no wall-clock time of their own, so the transport stamps each frame with
 * [receivedAtUs] on its monotonic clock; that is the source clock.
 */
interface MachineDecoder {
    /** [path] tells which socket the frame came from when a machine streams over several. */
    fun decodeText(text: String, receivedAtUs: Long, path: String): List<MachineMessage> = emptyList()

    fun decodeBinary(bytes: ByteArray, receivedAtUs: Long, path: String): List<MachineMessage> = emptyList()

    /** Called when a session starts, so state from a dropped connection does not leak in. */
    fun reset() {}
}

/** History is fetched over the machine's own HTTP API where it has one. */
interface MachineHistory {
    suspend fun list(limit: Int): List<ShotSummary>

    suspend fun load(id: String): Shot

    object None : MachineHistory {
        override suspend fun list(limit: Int): List<ShotSummary> = emptyList()

        override suspend fun load(id: String): Shot = throw NoSuchElementException("this source has no shot history")
    }
}

/**
 * Shared transport for machines and bridges that stream JSON over WebSockets: one socket at `ws://<host>/ws` for
 * Gaggiuino, several for a machine that splits its data across sockets. Connects while [start]'s scope is alive and
 * retries every 5 s; if any socket of a session drops, the session restarts. Frames from all sockets reach the decoder
 * one at a time, in arrival order.
 *
 * A machine that closes a socket within [busyWindowUs] of accepting it is reported as Busy: ESPAsyncWebServer drops
 * clients beyond its limit (see the handover's "accepts 3 sockets").
 */
class WebSocketShotSource(
    override val kind: SourceKind,
    override val capabilities: Capabilities,
    private val client: HttpClient,
    private val host: String,
    private val port: Int = 80,
    private val paths: List<String> = listOf("/ws"),
    private val decoder: MachineDecoder,
    private val historyApi: MachineHistory = MachineHistory.None,
    private val nowUs: () -> Long = MonotonicClock::nowUs,
    private val busyWindowUs: Long = 2_000_000,
) : ShotSource {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _live = MutableSharedFlow<Sample>(extraBufferCapacity = 256)
    override val live: SharedFlow<Sample> = _live.asSharedFlow()

    private val _brew = MutableSharedFlow<BrewEvent>(extraBufferCapacity = 16)
    override val brewEvents: SharedFlow<BrewEvent> = _brew.asSharedFlow()

    private val _frames = MutableSharedFlow<String>(extraBufferCapacity = 64)
    /** Raw text frames, for the machine log screen. */
    val frames: SharedFlow<String> = _frames.asSharedFlow()

    /** Starts the connection loop in [scope]; cancel the job when the app leaves the foreground. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        keepConnected(_connection) { onConnected ->
            decoder.reset()
            val decode = Mutex()
            var opened = 0
            coroutineScope {
                for (path in paths) launch {
                    client.webSocket(host = host, port = port, path = path) {
                        val openedAt = nowUs()
                        if (++opened == paths.size) onConnected()
                        for (frame in incoming) {
                            val at = nowUs()
                            val messages = decode.withLock {
                                when (frame) {
                                    is Frame.Text ->
                                        frame.readText().let {
                                            _frames.tryEmit(it)
                                            decoder.decodeText(it, at, path)
                                        }
                                    is Frame.Binary -> decoder.decodeBinary(frame.readBytes(), at, path)
                                    else -> emptyList()
                                }
                            }
                            for (m in messages) when (m) {
                                is MachineMessage.Reading -> _live.emit(m.sample)
                                is MachineMessage.Brew -> _brew.emit(m.event)
                                MachineMessage.Ignored -> Unit
                            }
                        }
                        if (nowUs() - openedAt < busyWindowUs) throw SourceBusyException()
                    }
                    // One socket closing ends the session; keepConnected reconnects all of them.
                    throw IllegalStateException("connection closed")
                }
            }
        }
    }

    override suspend fun history(limit: Int): List<ShotSummary> = historyApi.list(limit)

    override suspend fun loadShot(id: String): Shot = historyApi.load(id)
}

object MonotonicClock {
    private val start = TimeSource.Monotonic.markNow()

    fun nowUs(): Long = start.elapsedNow().inWholeMicroseconds
}
