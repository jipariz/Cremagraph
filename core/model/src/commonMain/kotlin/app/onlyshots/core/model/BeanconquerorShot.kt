package app.onlyshots.core.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A Beanconqueror brew: the JSON the app uploads, and what visualizer.coffee hands back for it under `brewdata` (the
 * upload untouched, plus a `parser` key). The readings sit in `brewFlow`, one list per sensor, each at its own rate:
 * ```
 * { "brew": { "config": { "unix_timestamp": 1792684688 } }, "preparation": { "name": "Flair pro 3" },
 *   "brewFlow": {
 *     "weight":          [{ "timestamp": "09:59:16.635", "brew_time": "0.0", "actual_weight": 0 }, …],
 *     "realtimeFlow":    [{ "timestamp": "09:59:16.635", "flow_value": 0 }, …],
 *     "pressureFlow":    [{ "timestamp": "09:59:16.707", "actual_pressure": 0.03 }, …],
 *     "temperatureFlow": [{ "timestamp": "21:46:13.600", "actual_temperature": 23.5 }, …],
 *     "waterDispensedFlowSecond": [{ "timestamp": "…", "actual": 1.2 }, …] } }
 * ```
 *
 * `timestamp` is the phone's wall clock, time of day only; `brew_time` is a rounded string and is not used (the
 * visualizer does not either). Readings are lined up by timestamp: one sample per weight reading (the densest list, the
 * longest one when there is no weight), the other sensors taken from their nearest reading, or left out when none is
 * close. Every list may be missing: older exports carry only some.
 *
 * ponytail: shape from `src/classes/brew/brewFlow.ts` of graphefruit/Beanconqueror at commit
 * 094aa368c4af34acf03425ae44d53c3ada30c8ef and `app/models/shot_chart/parsed_shot.rb` of miharekar/visualizer at commit
 * 4048586935aa28c083c73ad46af65e752900161e, checked against live shots on 2026-10-22.
 */
object BeanconquerorShot {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** A sensor's reading counts for a sample only this close to it. */
    private const val NEAREST_MS = 250L

    fun looksLike(root: JsonObject): Boolean = root["brewdata"] is JsonObject || root["brewFlow"] is JsonObject

    fun parse(root: JsonObject, fallbackId: String, now: () -> Instant = { Clock.System.now() }): Shot {
        val brew = root["brewdata"] as? JsonObject ?: root
        val flowData = brew["brewFlow"] as? JsonObject ?: throw NotAShotFile("no brewFlow")
        val weight = flowData.readings("weight", "actual_weight")
        val weightFlow = flowData.readings("realtimeFlow", "flow_value")
        val pressure = flowData.readings("pressureFlow", "actual_pressure")
        val temperature = flowData.readings("temperatureFlow", "actual_temperature")
        val flow = flowData.readings("waterDispensedFlowSecond", "actual")
        val all = listOf(weight, weightFlow, pressure, temperature, flow)
        if (listOf(weight, weightFlow, pressure).all { it.isEmpty() }) throw NotAShotFile("no readings")

        val startMs = all.mapNotNull { it.firstOrNull()?.first }.min()
        val clock = weight.ifEmpty { all.maxBy { it.size } }
        val samples = clock.map { (t, _) ->
            Sample(
                tUs = (t - startMs) * 1_000,
                pressure = pressure.near(t),
                flow = flow.near(t)?.coerceAtLeast(0f),
                weight = weight.near(t),
                weightFlow = weightFlow.near(t)?.coerceAtLeast(0f),
                temperature = temperature.near(t),
            )
        }

        val config = (brew["brew"] as? JsonObject)?.get("config") as? JsonObject
        val id =
            root.text("id")?.let { "visualizer-$it" } ?: config?.text("uuid")?.let { "beanconqueror-$it" } ?: fallbackId
        return Shot(
            id = id,
            source = SourceKind.VISUALIZER,
            profileName =
                (brew["preparation"] as? JsonObject)?.text("name") ?: root.text("profile_title") ?: "Beanconqueror",
            startedAt = config?.number("unix_timestamp")?.let { Instant.fromEpochSeconds(it.toLong()) } ?: now(),
            durationUs = (all.mapNotNull { it.lastOrNull()?.first }.max() - startMs) * 1_000,
            finalWeight = weight.lastOrNull()?.second,
            samples = samples,
            phaseStartsUs = emptyList(),
        )
    }

    /**
     * One sensor's readings as (ms of the day, value), in order. A time earlier than the one before it is read as past
     * midnight, so a brew that crosses it keeps counting up.
     */
    private fun JsonObject.readings(list: String, field: String): List<Pair<Long, Float>> {
        val rows = this[list] as? JsonArray ?: return emptyList()
        var dayOffset = 0L
        var last = Long.MIN_VALUE
        return rows.mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            var t = o.text("timestamp")?.let(::timeOfDayMs) ?: return@mapNotNull null
            val v = (o[field] as? JsonPrimitive)?.number ?: return@mapNotNull null
            if (t + dayOffset < last) dayOffset += DAY_MS
            t += dayOffset
            last = t
            t to v.toFloat()
        }
    }

    /** `HH:mm:ss.SSS` (fraction optional) as milliseconds since midnight. */
    internal fun timeOfDayMs(text: String): Long? {
        val parts = text.trim().split(':')
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toDoubleOrNull() ?: return null
        return ((h * 60 + m) * 60) * 1000 + (s * 1000).toLong()
    }

    /** The value of the reading nearest [t], if one is within [NEAREST_MS]. */
    private fun List<Pair<Long, Float>>.near(t: Long): Float? {
        if (isEmpty()) return null
        var lo = 0
        var hi = size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (this[mid].first < t) lo = mid + 1 else hi = mid
        }
        val best = listOfNotNull(getOrNull(lo - 1), getOrNull(lo)).minBy { kotlin.math.abs(it.first - t) }
        return best.second.takeIf { kotlin.math.abs(best.first - t) <= NEAREST_MS }
    }
}
