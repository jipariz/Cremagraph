package app.onlyshots.core.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** The file, or the body a service handed back, holds no shot this app can read. */
class NotAShotFile(message: String) : IllegalArgumentException(message)

/**
 * Every shot file the app reads, told apart by shape: a Gaggiuino's own (`datapoints`, [GaggiuinoShotFile]), a
 * visualizer.coffee shot (`timeframe` + `data`, [VisualizerShot]) and a Beanconqueror brew (`brewFlow`, on its own or
 * under the visualizer's `brewdata`, [BeanconquerorShot]).
 */
object ShotFiles {
    internal val json = Json { ignoreUnknownKeys = true }

    /**
     * @param fallbackId used when the file carries no id of its own.
     * @param now stamps a file with no start time, so the shot still sorts somewhere sensible.
     */
    fun parse(text: String, fallbackId: String, now: () -> Instant = { Clock.System.now() }): Shot {
        val root =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw NotAShotFile("not JSON")
            }
        return parse(root, fallbackId, now)
    }

    fun parse(root: JsonObject, fallbackId: String, now: () -> Instant = { Clock.System.now() }): Shot =
        when {
            VisualizerShot.looksLike(root) -> VisualizerShot.parse(root, fallbackId, now)
            BeanconquerorShot.looksLike(root) -> BeanconquerorShot.parse(root, fallbackId, now)
            else -> GaggiuinoShotFile.parse(root, fallbackId, now)
        }
}

/** A number, or a number written as a string (the visualizer sends a Decent's readings as strings). */
internal val JsonPrimitive.number: Double?
    get() = doubleOrNull ?: contentOrNull?.trim()?.toDoubleOrNull()

internal fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.number
