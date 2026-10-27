package app.onlyshots.sources.machine

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.BrewEvent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * GaggiMate WebSocket messages as sent by `src/display/plugins/WebSocketHandler.cpp` and described by
 * `docs/websocket-api.yaml` in `jniebuhr/gaggimate` (master `b78beee4dbcc1cd59946256f05e5cafa5450ec60`, 2026-10-18).
 * JSON text frames on `/ws`, each tagged with `tp`; only `evt:status` is read.
 *
 * **`evt:status` is partial by design.** A fast frame every 500 ms carries the live readings and the running process; a
 * slow frame carries state that changes rarely, and is sent in full to a client right as it connects and at least every
 * 10 s. A key that is absent keeps its last value, a key sent as null clears it, so the decoder keeps a merged status
 * rather than reading each frame on its own.
 *
 * Fast frame: `ct` current temperature °C, `tt` target temperature, `pr` pressure bar, `pt` target pressure, `fl` pump
 * flow ml/s, `tf` target flow, `cw` the active scale's weight in grams, and `process`, which is null when nothing runs
 * and otherwise holds `a` (1 while active, 0 once finished), `s` (`brew`, `infusion` or `grind`), `l` the phase name,
 * `e` milliseconds since the process started, `u` 1 for a utility run, and `tt`/`pt`/`pp` the phase's target and
 * progress. Slow frame: `m` machine mode, `p` the selected profile, and `cp`, whether the machine has a pressure sensor
 * at all.
 *
 * A shot is a `brew` or `infusion` process that is not a utility run. It starts at `receivedAt − e`, so a late first
 * frame does not shift the graph, and ends when the process goes inactive, disappears, turns into a grind, restarts
 * (`e` jumps back) or simply stops arriving for [endTimeoutUs]. The end time is the machine's own last `e`, not the
 * moment the end was noticed.
 *
 * **Pressure is gated on `cp`.** A GaggiMate Standard has no pressure sensor and still sends `pr` as a flat zero;
 * reading that as a measurement would draw a zero line across every Standard user's graph. Until a slow frame says
 * otherwise the machine is taken to have one, so a Pro loses nothing in the first half second.
 */
class GaggiMateDecoder(private val endTimeoutUs: Long = 2_000_000) : MachineDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    /** The merged status: what the last frame to mention each key said. */
    private val status = HashMap<String, JsonPrimitive>()

    /** Source time at which `e` was 0, while a shot runs. */
    private var anchorUs: Long? = null
    private var lastElapsedMs = -1L
    private var lastShotFrameUs = 0L

    /** Phase names in the order this shot showed them; a sample's phase is its position here. */
    private val phases = ArrayList<String>()

    override fun reset() {
        status.clear()
        anchorUs = null
        lastElapsedMs = -1
        phases.clear()
    }

    override fun decodeText(text: String, receivedAtUs: Long, path: String): List<MachineMessage> {
        val frame =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return listOf(MachineMessage.Ignored)
            }
        if (frame["tp"]?.jsonPrimitive?.contentOrNull != STATUS) return listOf(MachineMessage.Ignored)
        merge(frame)

        val process = frame["process"] as? JsonObject
        // The key is absent in a slow frame (nothing to say) and an explicit null in a fast one (nothing running).
        val mentionsProcess = frame.containsKey("process")
        return when {
            process != null && process.isBrew() -> onBrew(process, receivedAtUs)
            // A shot is running: the process going away, turning into a grind, or falling silent all end it. A slow
            // frame says nothing about the process, so it changes nothing.
            anchorUs != null ->
                if (mentionsProcess || receivedAtUs - lastShotFrameUs > endTimeoutUs) endShot()
                else listOf(MachineMessage.Ignored)
            else -> listOf(idle(receivedAtUs))
        }
    }

    /** Every key the frame carries overwrites the merged status; a null clears it. */
    private fun merge(frame: JsonObject) {
        for ((key, value) in frame) {
            val primitive = value as? JsonPrimitive ?: continue
            if (primitive.contentOrNull == null) status.remove(key) else status[key] = primitive
        }
    }

    private fun onBrew(process: JsonObject, at: Long): List<MachineMessage> {
        val elapsedMs = process.long("e") ?: return listOf(idle(at))
        val active = process.long("a") != 0L
        val out = ArrayList<MachineMessage>(3)
        // A shot that restarts without the machine ever reporting it idle: close the old one first.
        if (anchorUs != null && elapsedMs < lastElapsedMs) out += endShot()
        if (!active && anchorUs == null) return out + idle(at)
        val anchor =
            anchorUs
                ?: (at - elapsedMs * 1000).also {
                    anchorUs = it
                    out += MachineMessage.Brew(BrewEvent.Started(it))
                }
        lastElapsedMs = elapsedMs
        lastShotFrameUs = at
        out += MachineMessage.Reading(reading(anchor + elapsedMs * 1000, brewing = true, process = process))
        // The firmware keeps the finished process around with `a` at 0 and `e` frozen at the finish time.
        if (!active) out += endShot()
        return out
    }

    private fun endShot(): List<MachineMessage> {
        val anchor = anchorUs ?: return emptyList()
        anchorUs = null
        val end = anchor + lastElapsedMs.coerceAtLeast(0) * 1000
        lastElapsedMs = -1
        phases.clear()
        return listOf(MachineMessage.Brew(BrewEvent.Ended(end)))
    }

    /** The live readout between shots; the recorder ignores it. */
    private fun idle(at: Long) = MachineMessage.Reading(reading(at, brewing = false, process = null))

    private fun reading(tUs: Long, brewing: Boolean, process: JsonObject?): Sample {
        // A Standard has no pressure sensor and reports a flat zero; say nothing rather than draw that.
        val hasPressure = status["cp"]?.booleanOrNull ?: true
        val volumetric = process?.text("tt") == "volumetric"
        return Sample(
            tUs = tUs,
            pressure = if (hasPressure) float("pr") else null,
            flow = float("fl"),
            weight = float("cw"),
            temperature = float("ct"),
            targetPressure = if (hasPressure) float("pt") else null,
            targetFlow = float("tf"),
            targetTemperature = float("tt"),
            targetWeight = if (volumetric) process?.float("pt") else null,
            phase = process?.text("l")?.let { phaseIndex(it) },
            brewing = brewing,
        )
    }

    /** Phases arrive by name, so they are numbered in the order this shot shows them. */
    private fun phaseIndex(name: String): Int {
        val known = phases.indexOf(name)
        if (known >= 0) return known
        phases += name
        return phases.lastIndex
    }

    private fun JsonObject.isBrew(): Boolean =
        text("s") in BREW_STAGES &&
            // A flush, backflush or descaling run pumps water but is not a shot.
            long("u") != 1L

    private fun float(key: String): Float? = status[key]?.floatOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.float(key: String): Float? =
        (this[key] as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val STATUS = "evt:status"

        /** The process stages that make a shot; `grind` and everything else do not. */
        private val BREW_STAGES = setOf("brew", "infusion")
    }
}

/**
 * A GaggiMate on the local network. [client] needs the WebSockets plugin installed.
 *
 * Weight is declared whether or not a scale is paired: GaggiMate's scale is the machine's own and can connect at any
 * moment, while capabilities are fixed when the source is built. A shot taken without one simply carries no weight.
 */
fun gaggiMateSource(client: HttpClient, host: String, port: Int = 80): WebSocketShotSource =
    WebSocketShotSource(
        kind = SourceKind.GAGGIMATE,
        capabilities = Capabilities.of(SourceKind.GAGGIMATE, hasScale = true),
        client = client,
        host = host,
        port = port,
        decoder = GaggiMateDecoder(),
        historyApi = GaggiMateHistory(client, host, port),
    )
