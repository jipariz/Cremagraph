package app.onlyshots.core.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A GaggiMate shot as its web UI exports it: the JSON the Shot Analyzer's "export" hands out, built by
 * `web/src/pages/ShotAnalyzer/services/LibraryService.js` in `jniebuhr/gaggimate` (master
 * `b78beee4dbcc1cd59946256f05e5cafa5450ec60`, 2026-10-18). The machine's own storage is binary instead
 * ([GaggiMateShotLog]); this is the shape a user gets by pressing export in the browser, and the one that reaches the
 * web editor, which cannot talk to the machine at all.
 *
 * One object per reading, already in real units, time in milliseconds:
 * ```
 * { "id": "123", "profile": "Londinium", "profileId": "…", "timestamp": 1792338179, "duration": 28400,
 *   "volume": 36.2,
 *   "samples": [{ "t": 250, "ct": 93.1, "tt": 93, "cp": 6.2, "tp": 9, "fl": 2.1, "tf": 2, "pf": 1.8,
 *                 "vf": 1.7, "v": 4.2, "ev": 4.0, "pr": 0.4, "wp": 12.1, "phaseNumber": 1 }] }
 * ```
 *
 * `pf` (puck flow), `ev` (estimated weight), `pr` (puck resistance) and `wp` (water pumped) have no place in a [Shot]
 * and are dropped rather than drawn as something they are not.
 */
object GaggiMateShotFile {
    private const val MILLISECOND_US = 1_000L

    /**
     * Whether this is a GaggiMate export: a `samples` list of objects carrying the machine's own short keys. The two
     * other shapes with a reading list ([VisualizerShot], [BeanconquerorShot]) are matched before this one and name
     * their arrays differently, so the check only has to be sure it is not a Gaggiuino file, which uses `datapoints`.
     */
    fun looksLike(root: JsonObject): Boolean {
        val first = (root["samples"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return false
        return first.containsKey("ct") || first.containsKey("cp")
    }

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
        val rows = (root["samples"] as? JsonArray) ?: throw NotAShotFile("no samples")
        val samples =
            rows
                .mapNotNull { element ->
                    val o = element as? JsonObject ?: return@mapNotNull null
                    val t = o.number("t") ?: return@mapNotNull null
                    Sample(
                        tUs = t.toLong() * MILLISECOND_US,
                        pressure = o.float("cp"),
                        flow = o.float("fl"),
                        weight = o.float("v"),
                        weightFlow = o.float("vf"),
                        temperature = o.float("ct"),
                        targetPressure = o.float("tp"),
                        targetFlow = o.float("tf"),
                        targetTemperature = o.float("tt"),
                        phase = o.number("phaseNumber")?.toInt(),
                    )
                }
                .sortedBy { it.tUs }
        if (samples.isEmpty()) throw NotAShotFile("no readings")
        val id = root.text("id") ?: fallbackId
        val phaseStarts =
            samples.zipWithNext().filter { (a, b) -> b.phase != null && b.phase != a.phase }.map { it.second.tUs }
        return Shot(
            id = id,
            source = SourceKind.GAGGIMATE,
            profileName = root.text("profile") ?: "Shot $id",
            startedAt = root.number("timestamp")?.let { Instant.fromEpochSeconds(it.toLong()) } ?: now(),
            durationUs = root.number("duration")?.times(MILLISECOND_US)?.toLong() ?: samples.last().tUs,
            finalWeight = root.number("volume")?.toFloat() ?: samples.lastOrNull { it.weight != null }?.weight,
            samples = samples,
            phaseStartsUs = if (samples.first().phase != null) listOf(0L) + phaseStarts else phaseStarts,
        )
    }

    private fun JsonObject.float(key: String): Float? =
        (this[key] as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() }
}
