package app.onlyshots.core.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A Gaggiuino shot as a file: what `GET /api/shots/{id}` returns, and what baristagiuino and the machine's web UI hand
 * back as a `.shot` download. Parsing it lets a shot the machine uploaded on its own reach a video that was filmed
 * without OnlyShots connected to anything. A shot the machine sent to visualizer.coffee comes back in the visualizer's
 * own shape instead ([VisualizerShot]); [ShotFiles] tells the two apart.
 *
 * The shape is parallel arrays under `datapoints`, every value scaled by ten and time in deciseconds:
 * ```
 * { "id": 42, "timestamp": 1760000000, "duration": 285, "profileName": "Londinium",
 *   "datapoints": { "timeInShot": [0, 10, 20], "pressure": [0, 60, 90], "pumpFlow": [...],
 *                   "shotWeight": [...], "weightFlow": [...], "temperature": [...],
 *                   "targetPressure": [...], "targetPumpFlow": [...], "targetTemperature": [...],
 *                   "phaseTransitions": [{ "t": 40, "phase": 1, "name": "infusion" }] } }
 * ```
 *
 * Newer firmware (and its web UI's "download") writes the readings as a list of objects instead, in real units with
 * time in milliseconds, a `phaseIndex` on each, and the profile under `profile`:
 * ```
 * { "id": 121, "timestamp": 1792338179, "duration": 234, "profile": { "name": "Flimsy Light", ... },
 *   "datapoints": [{ "timeInShot": 100, "pressure": 0.5, "pumpFlow": 0, "shotWeight": 0, "phaseIndex": 0, ... }] }
 * ```
 *
 * Both shapes are read.
 *
 * ponytail: the scaling and the field names come from reading other clients (`mxkissnr/gaggiuino-local-profiler`, whose
 * GaggiMate adapter reproduces this payload, and whose charts divide every series by ten), because neither Gaggiuino's
 * REST docs nor its web UI document the body. Anything missing is left null rather than guessed, so a firmware that
 * renames a series loses that curve instead of drawing a wrong one.
 */
object GaggiuinoShotFile {
    /** Deciseconds to microseconds. */
    private const val DECISECOND_US = 100_000L

    /** Milliseconds to microseconds, for the newer shape. */
    private const val MILLISECOND_US = 1_000L

    /** Every reading is stored as an integer ten times its real value. */
    private const val SCALE = 10f

    /**
     * @param fallbackId used when the file carries no id of its own.
     * @param now stamps a file with no timestamp, so the shot still sorts somewhere sensible.
     */
    fun parse(text: String, fallbackId: String, now: () -> Instant = { Clock.System.now() }): Shot {
        val root =
            try {
                ShotFiles.json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw NotAShotFile("not JSON")
            }
        return parse(root, fallbackId, now)
    }

    fun parse(root: JsonObject, fallbackId: String, now: () -> Instant = { Clock.System.now() }): Shot {
        // A payload wrapped as `{"shot": …}` is read too; Gaggiuino itself does not wrap it.
        val shot = (root["shot"] as? JsonObject) ?: root
        val (samples, phaseStartsUs) =
            when (val points = shot["datapoints"]) {
                is JsonObject -> columns(points)
                is JsonArray -> rows(points)
                else -> throw NotAShotFile("no datapoints")
            }
        val id = shot["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackId
        val duration = shot.long("duration")?.times(DECISECOND_US) ?: samples.last().tUs
        return Shot(
            id = id,
            source = SourceKind.GAGGIUINO,
            profileName = shot.text("profileName") ?: (shot["profile"] as? JsonObject)?.text("name") ?: "Shot $id",
            startedAt = shot.long("timestamp")?.let { Instant.fromEpochSeconds(it) } ?: now(),
            durationUs = duration,
            finalWeight = samples.lastOrNull { it.weight != null }?.weight,
            samples = samples,
            phaseStartsUs = phaseStartsUs,
        )
    }

    /** The older shape: parallel arrays, ten times the real values, time in deciseconds. */
    private fun columns(points: JsonObject): Pair<List<Sample>, List<Long>> {
        val times = points.numbers("timeInShot") ?: throw NotAShotFile("no timeInShot")
        if (times.isEmpty()) throw NotAShotFile("no readings")
        val pressure = points.scaled("pressure", times.size)
        val flow = points.scaled("pumpFlow", times.size)
        val weight = points.scaled("shotWeight", times.size)
        val weightFlow = points.scaled("weightFlow", times.size)
        val temperature = points.scaled("temperature", times.size)
        val targetPressure = points.scaled("targetPressure", times.size)
        val targetFlow = points.scaled("targetPumpFlow", times.size)
        val targetTemperature = points.scaled("targetTemperature", times.size)
        val phases = phaseAt(points)
        val samples = times.mapIndexed { i, t ->
            Sample(
                tUs = t * DECISECOND_US,
                pressure = pressure?.get(i),
                flow = flow?.get(i),
                weight = weight?.get(i),
                weightFlow = weightFlow?.get(i),
                temperature = temperature?.get(i),
                targetPressure = targetPressure?.get(i),
                targetFlow = targetFlow?.get(i),
                targetTemperature = targetTemperature?.get(i),
                phase = phases?.let { p -> p.lastOrNull { it.first <= t }?.second },
            )
        }
        return samples to phases?.map { it.first * DECISECOND_US }?.distinct().orEmpty()
    }

    /**
     * The newer shape: one object per reading, real units, time in milliseconds; a phase starts where its index does.
     */
    private fun rows(points: JsonArray): Pair<List<Sample>, List<Long>> {
        val samples =
            points
                .mapNotNull { element ->
                    val o = element as? JsonObject ?: return@mapNotNull null
                    val t = o.long("timeInShot") ?: return@mapNotNull null
                    Sample(
                        tUs = t * MILLISECOND_US,
                        pressure = o.float("pressure"),
                        flow = o.float("pumpFlow"),
                        weight = o.float("shotWeight"),
                        weightFlow = o.float("weightFlow"),
                        temperature = o.float("temperature"),
                        targetPressure = o.float("targetPressure"),
                        targetFlow = o.float("targetPumpFlow"),
                        targetTemperature = o.float("targetTemperature"),
                        phase = o.long("phaseIndex")?.toInt(),
                    )
                }
                .sortedBy { it.tUs }
        if (samples.isEmpty()) throw NotAShotFile("no readings")
        val phaseStarts =
            samples.zipWithNext().filter { (a, b) -> b.phase != null && b.phase != a.phase }.map { it.second.tUs }
        return samples to phaseStarts
    }

    /** Phase number in effect from each transition time, in deciseconds, earliest first. */
    private fun phaseAt(points: JsonObject): List<Pair<Long, Int>>? {
        val transitions = points["phaseTransitions"] as? JsonArray ?: return null
        val parsed = transitions.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val t = o.long("t") ?: return@mapNotNull null
            val phase = o.long("phase")?.toInt() ?: return@mapNotNull null
            t to phase
        }
        return parsed.takeIf { it.isNotEmpty() }?.sortedBy { it.first }
    }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.numbers(key: String): List<Long>? =
        (this[key] as? JsonArray)?.map { it.jsonPrimitive.longOrNull ?: 0L }

    /**
     * A series as real units, or null when the file does not carry it. A series shorter than the clock is padded with
     * nulls rather than dropped: the readings it does have are still true.
     */
    private fun JsonObject.scaled(key: String, size: Int): List<Float?>? {
        val array = (this[key] as? JsonArray) ?: return null
        if (array.isEmpty()) return null
        return List(size) { i -> array.getOrNull(i)?.jsonPrimitive?.floatOrNull?.div(SCALE) }
    }
}
