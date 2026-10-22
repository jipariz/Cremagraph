package app.onlyshots.core.model

import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A shot as visualizer.coffee hands it back (`GET /api/shots/{id}`, or `/api/shots/shared?code=…&with_data=1`). The
 * visualizer keeps no uploaded file: it reads every machine's into one shape, so a Decent, a Gaggiuino, a GaggiMate and
 * a Meticulous shot all come back alike:
 * ```
 * { "id": "832f7109-…", "profile_title": "Zer0", "start_time": "2026-10-22T15:22:50.000Z", "duration": 34.9,
 *   "drink_weight": "48.7", "timeframe": [0.5, 0.7, …],
 *   "data": { "espresso_pressure": [0.1, …], "espresso_flow": […], "espresso_weight": […],
 *             "espresso_flow_weight": […], "espresso_temperature_mix": […], "espresso_pressure_goal": […],
 *             "espresso_flow_goal": […], "espresso_temperature_goal": […], "espresso_state_change": […] } }
 * ```
 *
 * `timeframe` is seconds from the start, one per reading; each series under `data` lines up with it. Which series are
 * there depends on the machine. A Decent's readings arrive as strings (`"0.057"`) and its goals as `-1` where there is
 * none; the others' as numbers. A Beanconqueror brew carries `brewdata` instead, read by [BeanconquerorShot].
 *
 * ponytail: field names from `app/models/shot/jsonable.rb` and the parsers in `app/models/parsers/` of
 * miharekar/visualizer at commit 4048586935aa28c083c73ad46af65e752900161e, checked against live shots on 2026-10-22.
 */
object VisualizerShot {
    private const val SECOND_US = 1_000_000.0

    fun looksLike(root: JsonObject): Boolean = root["timeframe"] is JsonArray && root["data"] is JsonObject

    fun parse(root: JsonObject, fallbackId: String, now: () -> Instant = { Clock.System.now() }): Shot {
        val times =
            (root["timeframe"] as? JsonArray)?.map { (it as? JsonPrimitive)?.number }
                ?: throw NotAShotFile("no timeframe")
        val data = root["data"] as? JsonObject ?: throw NotAShotFile("no data")
        fun series(key: String): List<Double?>? =
            (data[key] as? JsonArray)?.takeIf { it.isNotEmpty() }?.map { (it as? JsonPrimitive)?.number }
        // A goal of -1 (a Decent's) means there is none at that moment.
        fun goal(key: String): List<Double?>? = series(key)?.map { v -> v?.takeIf { it >= 0 } }

        val pressure = series("espresso_pressure")
        val flow = series("espresso_flow")
        val weight = series("espresso_weight")
        val weightFlow = series("espresso_flow_weight")
        val temperature = series("espresso_temperature_mix") ?: series("espresso_temperature_basket")
        val targetPressure = goal("espresso_pressure_goal")
        val targetFlow = goal("espresso_flow_goal")
        val targetTemperature = goal("espresso_temperature_goal")
        if (listOfNotNull(pressure, flow, weight).isEmpty()) throw NotAShotFile("no readings")

        val phaseAt = phases(series("espresso_state_change"))
        val samples =
            times
                .mapIndexedNotNull { i, t ->
                    t ?: return@mapIndexedNotNull null
                    Sample(
                        tUs = (t * SECOND_US).roundToLong(),
                        pressure = pressure.at(i),
                        flow = flow.at(i),
                        weight = weight.at(i),
                        weightFlow = weightFlow.at(i),
                        temperature = temperature.at(i),
                        targetPressure = targetPressure.at(i),
                        targetFlow = targetFlow.at(i),
                        targetTemperature = targetTemperature.at(i),
                        phase = phaseAt?.get(i),
                    )
                }
                .sortedBy { it.tUs }
        if (samples.isEmpty()) throw NotAShotFile("no readings")

        val id = root.text("id")?.let { "visualizer-$it" } ?: fallbackId
        return Shot(
            id = id,
            source = SourceKind.VISUALIZER,
            profileName = root.text("profile_title") ?: "Shot",
            startedAt = root.text("start_time")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: now(),
            durationUs = root.number("duration")?.let { (it * SECOND_US).roundToLong() } ?: samples.last().tUs,
            finalWeight =
                root.number("drink_weight")?.toFloat()?.takeIf { it > 0f }
                    ?: samples.lastOrNull { it.weight != null }?.weight,
            samples = samples,
            phaseStartsUs =
                if (phaseAt == null) emptyList()
                else
                    samples
                        .zipWithNext()
                        .filter { (a, b) -> b.phase != a.phase }
                        .map { it.second.tUs }
                        .let { listOf(samples.first().tUs) + it },
        )
    }

    private fun List<Double?>?.at(i: Int): Float? = this?.getOrNull(i)?.toFloat()

    /**
     * A Decent marks each new phase by flipping the sign of `espresso_state_change` (`10000000`, `-10000000`, …), so
     * the phase number at each reading counts the flips so far. Null when the shot carries no such series or never
     * flips.
     */
    private fun phases(change: List<Double?>?): List<Int>? {
        change ?: return null
        var phase = 0
        var last = 0.0
        val out = change.map { v ->
            val s = v?.sign ?: 0.0
            if (s != 0.0) {
                if (last != 0.0 && s != last) phase++
                last = s
            }
            phase
        }
        return out.takeIf { it.last() > 0 }
    }
}
