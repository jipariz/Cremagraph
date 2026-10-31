package app.onlyshots.sources.machine

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.BrewEvent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Gaggiuino WebSocket messages as sent by `webserver/src/server/websocket/websocket.cpp` on `release/stm32-blackpill`
 * (bddc57a, 2024-09-28). JSON text frames `{ "action", "data" }`:
 *
 * - `shot_data_update`, about every 100 ms while a shot runs: `timeInShot` (ms), `pressure`, `pumpFlow`, `weightFlow`,
 *   `temperature`, `shotWeight`, `waterPumped`, `targetTemperature`, `targetPumpFlow`, `targetPressure`.
 * - `sensor_data_update`, about every 1 s: `brewActive` (the brew switch), `steamActive`, `scalesPresent`,
 *   `temperature`, `waterLvl`, `pressure`, `pumpFlow`, `weightFlow`, `weight`.
 * - `log_record`: ignored.
 *
 * The shot starts at the first shot message (placed at `receivedAt − timeInShot`, so a late first frame does not shift
 * the graph) and ends when shot messages stop for [endTimeoutUs], the brew switch goes off, or `timeInShot` jumps back
 * (a new shot). The end time is the last `timeInShot`, not the moment the end was noticed.
 *
 * Newer firmware (seen on core `6ea560e`, STM32F411-PCB-PCA, Oct 2026) sends binary protobuf frames instead: an
 * envelope `{ 1: action, 2: data }` where `d_sensor_snap`, about three times a second whether brewing or not, carries
 * `brewActive` (1), `temperature` (4), `pressure` (6), `pumpFlow` (7), `weightFlow` (8) and `weight` (9) as unscaled
 * floats. Field numbers from live frames, cross-checked against the reverse-engineered schema in
 * `mxkissnr/gaggiuino-local-profiler` (read as a reference, nothing copied). There is no time in shot, so the shot
 * starts at the first snapshot with `brewActive` and ends at the last one before it turns off.
 */
class GaggiuinoDecoder(private val endTimeoutUs: Long = 1_500_000) : MachineDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    /** Source time at which `timeInShot` was 0, while a shot runs. */
    private var anchorUs: Long? = null
    private var lastTimeInShotMs = -1L
    private var lastShotFrameUs = 0L

    /** Binary firmware: when the current shot started and when it was last seen brewing, on the source clock. */
    private var binaryStartUs: Long? = null
    private var lastBrewingUs = 0L

    /** Binary firmware: the newest targets from `d_shot_snap`, which the sensor stream does not carry. */
    private var targets: Targets? = null

    override fun reset() {
        anchorUs = null
        lastTimeInShotMs = -1
        binaryStartUs = null
        targets = null
    }

    override fun decodeBinary(bytes: ByteArray, receivedAtUs: Long, path: String): List<MachineMessage> {
        val (action, data) = binaryEnvelope(bytes) ?: return listOf(MachineMessage.Ignored)
        val f =
            try {
                protoFields(data).associateBy { it.number }
            } catch (e: IllegalArgumentException) {
                return listOf(MachineMessage.Ignored)
            }
        return when (action) {
            SENSOR_SNAPSHOT -> onSensorSnapshot(f, receivedAtUs)
            SHOT_SNAPSHOT -> onShotSnapshot(f, receivedAtUs)
            else -> listOf(MachineMessage.Ignored)
        }
    }

    /**
     * `d_sensor_snap`: the brew switch, and every reading the shot is drawn from. Its temperature is the machine's own
     * thermocouple, which climbs through an extraction; the shot stream reports the setpoint instead, and this is the
     * measurement, so it is the one kept.
     */
    private fun onSensorSnapshot(f: Map<Int, ProtoField>, at: Long): List<MachineMessage> {
        val brewing = f[1]?.bool ?: false
        val out = ArrayList<MachineMessage>(2)
        if (brewing && binaryStartUs == null) {
            binaryStartUs = at
            out += MachineMessage.Brew(BrewEvent.Started(at))
        }
        if (!brewing && binaryStartUs != null) {
            binaryStartUs = null
            targets = null
            out += MachineMessage.Brew(BrewEvent.Ended(lastBrewingUs))
        }
        if (brewing) lastBrewingUs = at
        fun float(n: Int) = f[n]?.float?.takeIf { it.isFinite() }
        val t = targets.takeIf { brewing }
        out +=
            MachineMessage.Reading(
                Sample(
                    tUs = at,
                    pressure = float(6),
                    flow = float(7),
                    weight = float(9),
                    weightFlow = float(8),
                    temperature = float(4),
                    targetPressure = t?.pressure,
                    targetFlow = t?.flow,
                    targetTemperature = t?.temperature,
                    brewing = brewing,
                )
            )
        return out
    }

    /**
     * `d_shot_snap`, one per sample while a shot runs. The sensor stream carries no targets at all, so this is where
     * they come from; its own readings are left alone, the sensor stream having already measured them.
     *
     * Scaled integers, ten times the real value. `timeInShot` (field 1) is read past: Gaggiuino's docs call these "the
     * same convention as the shot-history JSON files", but this firmware's history is in real units and milliseconds,
     * so the two disagree about the unit and nothing here needs it.
     */
    private fun onShotSnapshot(f: Map<Int, ProtoField>, at: Long): List<MachineMessage> {
        fun scaled(n: Int) = f[n]?.takeIf { it.wireType == ProtoField.VARINT }?.varint?.toFloat()?.div(SHOT_SCALE)
        targets = Targets(temperature = scaled(8), flow = scaled(9), pressure = scaled(10))
        return listOf(MachineMessage.Ignored)
    }

    /** The targets of the sample the machine last reported, held for the readings that carry none. */
    private class Targets(val temperature: Float?, val flow: Float?, val pressure: Float?)

    override fun decodeText(text: String, receivedAtUs: Long, path: String): List<MachineMessage> {
        val root =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return listOf(MachineMessage.Ignored)
            }
        val data = root["data"] as? JsonObject ?: return listOf(MachineMessage.Ignored)
        return when (root["action"]?.jsonPrimitive?.content) {
            SHOT_DATA -> onShotData(data, receivedAtUs)
            SENSOR_DATA -> onSensorData(data, receivedAtUs)
            else -> listOf(MachineMessage.Ignored)
        }
    }

    private fun onShotData(d: JsonObject, at: Long): List<MachineMessage> {
        val timeMs = d.long("timeInShot") ?: return listOf(MachineMessage.Ignored)
        val out = ArrayList<MachineMessage>(3)
        if (anchorUs != null && timeMs < lastTimeInShotMs) out += endShot()
        val anchor =
            anchorUs
                ?: (at - timeMs * 1000).also {
                    anchorUs = it
                    out += MachineMessage.Brew(BrewEvent.Started(it))
                }
        lastTimeInShotMs = timeMs
        lastShotFrameUs = at
        out +=
            MachineMessage.Reading(
                Sample(
                    tUs = anchor + timeMs * 1000,
                    pressure = d.float("pressure"),
                    flow = d.float("pumpFlow"),
                    weight = d.float("shotWeight"),
                    weightFlow = d.float("weightFlow"),
                    temperature = d.float("temperature"),
                    targetPressure = d.float("targetPressure"),
                    targetFlow = d.float("targetPumpFlow"),
                    targetTemperature = d.float("targetTemperature"),
                    brewing = true,
                )
            )
        return out
    }

    private fun onSensorData(d: JsonObject, at: Long): List<MachineMessage> {
        val brewSwitch = d["brewActive"]?.jsonPrimitive?.booleanOrNull ?: false
        if (anchorUs != null) {
            val stale = at - lastShotFrameUs > endTimeoutUs
            return if (stale || !brewSwitch) endShot() else listOf(MachineMessage.Ignored)
        }
        // Idle reading between shots: the live readout before a shot; the recorder ignores it.
        return listOf(
            MachineMessage.Reading(
                Sample(
                    tUs = at,
                    pressure = d.float("pressure"),
                    flow = d.float("pumpFlow"),
                    weight = d.float("weight"),
                    weightFlow = d.float("weightFlow"),
                    temperature = d.float("temperature"),
                    brewing = false,
                )
            )
        )
    }

    private fun endShot(): List<MachineMessage> {
        val anchor = anchorUs ?: return emptyList()
        anchorUs = null
        val end = anchor + lastTimeInShotMs.coerceAtLeast(0) * 1000
        lastTimeInShotMs = -1
        return listOf(MachineMessage.Brew(BrewEvent.Ended(end)))
    }

    private fun JsonObject.float(key: String): Float? = this[key]?.jsonPrimitive?.floatOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    companion object {
        const val SHOT_DATA = "shot_data_update"
        const val SENSOR_DATA = "sensor_data_update"

        /** Binary firmware: live sensors, and system state (version, operation mode). */
        const val SENSOR_SNAPSHOT = "d_sensor_snap"

        /** Binary firmware: one per sample while a shot runs. */
        const val SHOT_SNAPSHOT = "d_shot_snap"

        /** `ShotSnapshotDto` stores every reading as ten times its real value. */
        private const val SHOT_SCALE = 10f
        const val SYSTEM_STATE = "d_sys_state"

        /** The `{ 1: action, 2: data }` envelope every binary frame comes in, or null when it is not one. */
        fun binaryEnvelope(bytes: ByteArray): Pair<String, ByteArray>? =
            try {
                val f = protoFields(bytes)
                val action = f.firstOrNull { it.number == 1 && it.wireType == ProtoField.LENGTH }?.string
                val data = f.firstOrNull { it.number == 2 && it.wireType == ProtoField.LENGTH }?.bytes ?: ByteArray(0)
                action?.let { it to data }
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}

/** A Gaggiuino on the local network. [client] needs the WebSockets plugin installed. */
fun gaggiuinoSource(client: HttpClient, host: String, port: Int = 80): WebSocketShotSource =
    WebSocketShotSource(
        kind = SourceKind.GAGGIUINO,
        capabilities = Capabilities.of(SourceKind.GAGGIUINO),
        client = client,
        host = host,
        port = port,
        decoder = GaggiuinoDecoder(),
        historyApi = GaggiuinoHistory(client, host, port),
    )
