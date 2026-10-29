package app.onlyshots.sources.machine

import app.onlyshots.core.model.SourceKind
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A kind of espresso machine reachable on the local network. Everything the app knows about a machine model is here:
 * how [NetworkScan] finds and recognises it, and how a live source is built for it. Supporting another machine is one
 * more implementation, added to [NetworkMachines.all].
 */
interface NetworkMachine {
    val kind: SourceKind
    val name: String
    val defaultPort: Int

    /** How a recording starts with this machine, which the screens put into words under the record action. */
    val autoStart: AutoStart

    /** Names the machine answers to on its own, probed when nothing else finds it (`gaggiuino.local`). */
    val hostnames: List<String>

    /** Whether an mDNS service found while browsing could be this machine; [identify] still has the final say. */
    fun matchesService(service: MdnsService): Boolean

    /**
     * Whether [host]:[port] is this machine. Quick and cheap, and releases anything it opens at once: an ESP32 web
     * server takes only a few clients, and the user's own browser may hold one of them.
     */
    suspend fun identify(client: HttpClient, host: String, port: Int, connectTimeoutMs: Long): Boolean

    /** The live source for the machine at [host]:[port], not started yet. */
    fun source(client: HttpClient, host: String, port: Int): WebSocketShotSource

    /** The machine's own shot history, read without a live connection (the phones' "compose a video"). */
    fun history(client: HttpClient, host: String, port: Int): MachineHistory = MachineHistory.None
}

/** What starts a recording on its own with a machine; the UI words it. */
enum class AutoStart {
    /** The machine's brew switch flipping on. */
    BREW_SWITCH,

    /** The machine starting a brew of its own accord, from its screen, a button or a profile. */
    PROCESS_START,
}

object NetworkMachines {
    val all: List<NetworkMachine> = listOf(Gaggiuino, GaggiMate)

    fun of(kind: SourceKind): NetworkMachine? = all.firstOrNull { it.kind == kind }
}

/**
 * Gaggiuino. It has no info endpoint, so it is recognised by what its socket at `/ws` says, even when idle: the public
 * release (`release/stm32-blackpill`) sends JSON `sensor_data_update` about every second, newer firmware binary
 * `d_sensor_snap` and `d_sys_state` frames several times a second (see [GaggiuinoDecoder]).
 */
object Gaggiuino : NetworkMachine {
    override val kind = SourceKind.GAGGIUINO
    override val name = "Gaggiuino"
    override val defaultPort = 80
    override val hostnames = listOf("gaggiuino.local")
    override val autoStart = AutoStart.BREW_SWITCH

    /** How long a connected socket gets to say something Gaggiuino-like: one sensor update, with room to spare. */
    const val FIRST_FRAME_MS = 2_500L

    private val json = Json { ignoreUnknownKeys = true }

    override fun matchesService(service: MdnsService): Boolean = service.name.contains("gaggiuino", ignoreCase = true)

    override suspend fun identify(client: HttpClient, host: String, port: Int, connectTimeoutMs: Long): Boolean =
        withTimeoutOrNull(connectTimeoutMs + FIRST_FRAME_MS) {
            try {
                var recognised = false
                client.webSocket(
                    host = host,
                    port = port,
                    path = "/ws",
                    request = { timeout { connectTimeoutMillis = connectTimeoutMs } },
                ) {
                    for (frame in incoming) {
                        val ours =
                            when (frame) {
                                is Frame.Text -> isGaggiuinoFrame(frame.readText())
                                is Frame.Binary -> isGaggiuinoFrame(frame.readBytes())
                                else -> false
                            }
                        if (ours) {
                            recognised = true
                            break
                        }
                    }
                }
                recognised
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        } ?: false

    override fun source(client: HttpClient, host: String, port: Int) = gaggiuinoSource(client, host, port)

    override fun history(client: HttpClient, host: String, port: Int): MachineHistory =
        GaggiuinoHistory(client, host, port)

    internal fun isGaggiuinoFrame(bytes: ByteArray): Boolean =
        GaggiuinoDecoder.binaryEnvelope(bytes)?.first in
            setOf(GaggiuinoDecoder.SENSOR_SNAPSHOT, GaggiuinoDecoder.SYSTEM_STATE)

    internal fun isGaggiuinoFrame(text: String): Boolean =
        try {
            json.parseToJsonElement(text).jsonObject["action"]?.jsonPrimitive?.content in
                setOf(GaggiuinoDecoder.SENSOR_DATA, GaggiuinoDecoder.SHOT_DATA)
        } catch (e: Exception) {
            false
        }
}

/**
 * GaggiMate. It answers `GET /api/status` with its mode and its two temperatures, which is cheaper to ask than a
 * WebSocket handshake and leaves the ESP32's few socket slots to the user's own browser. The live data arrives on `/ws`
 * as `evt:status` frames (see [GaggiMateDecoder]), and the machine keeps its own shot history ([GaggiMateHistory]).
 */
object GaggiMate : NetworkMachine {
    override val kind = SourceKind.GAGGIMATE
    override val name = "GaggiMate"
    override val defaultPort = 80
    override val hostnames = listOf("gaggimate.local")
    override val autoStart = AutoStart.PROCESS_START

    private val json = Json { ignoreUnknownKeys = true }

    override fun matchesService(service: MdnsService): Boolean = service.name.contains("gaggimate", ignoreCase = true)

    override suspend fun identify(client: HttpClient, host: String, port: Int, connectTimeoutMs: Long): Boolean =
        try {
            val response =
                client.get("http://$host:$port/api/status") {
                    timeout {
                        connectTimeoutMillis = connectTimeoutMs
                        requestTimeoutMillis = connectTimeoutMs + STATUS_READ_MS
                    }
                }
            response.status.isSuccess() && isGaggiMateStatus(response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    override fun source(client: HttpClient, host: String, port: Int) = gaggiMateSource(client, host, port)

    override fun history(client: HttpClient, host: String, port: Int): MachineHistory =
        GaggiMateHistory(client, host, port)

    /** How long the status body gets to arrive once the socket is open. */
    private const val STATUS_READ_MS = 2_000L

    /** Standby, brew, steam, water, grind. */
    private val MODES = 0..4

    /** `{"mode":1,"tt":93,"ct":92.4}`: all three, and a mode the firmware actually has. */
    internal fun isGaggiMateStatus(text: String): Boolean =
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val mode = root["mode"]?.jsonPrimitive?.intOrNull
            mode in MODES &&
                root["ct"]?.jsonPrimitive?.doubleOrNull != null &&
                root["tt"]?.jsonPrimitive?.doubleOrNull != null
        } catch (e: Exception) {
            false
        }
}
