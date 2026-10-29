package app.onlyshots.sources.machine

import app.onlyshots.core.model.GaggiuinoShotFile
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.summary
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A Gaggiuino's own shot history, over its HTTP API: `GET /api/shots/{id}` is the shot as a `.shot` file holds it (read
 * by [GaggiuinoShotFile]), and `GET /api/shots/latest` names the newest id. Shots are numbered one after another, so
 * the recent ones are the ids counting down from the newest.
 *
 * ponytail: `/api/shots/{id}` is the body the web UI's download hands out; the shape of `/api/shots/latest` was not
 * seen from a machine, so [latestId] takes `[{"lastShotId": "121"}]`, `{"lastShotId": 121}` or a bare number. The
 * release firmware (`release/stm32-blackpill`) has neither: there every call fails and the list is empty.
 */
class GaggiuinoHistory(private val client: HttpClient, private val host: String, private val port: Int = 80) :
    MachineHistory {
    private val json = Json { ignoreUnknownKeys = true }

    private fun url(path: String) = "http://$host:$port$path"

    /** The newest shot's id, or null when the machine does not say. */
    suspend fun latestId(): Long? {
        val response = client.get(url("/api/shots/latest")) { timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
        if (!response.status.isSuccess()) return null
        return parseLatest(response.bodyAsText())
    }

    /**
     * The [limit] newest shots, newest first. One request at a time (the machine's web server takes only a few
     * clients); an id that fails is skipped (a deleted shot), and three failures in a row end the list.
     */
    override suspend fun list(limit: Int): List<ShotSummary> {
        val latest = latestId() ?: return emptyList()
        val found = mutableListOf<ShotSummary>()
        var misses = 0
        var id = latest
        while (id > 0 && found.size < limit && misses < MAX_MISSES) {
            val shot = runCatchingNonCancel { load(id.toString()) }
            if (shot == null) misses++
            else {
                misses = 0
                found += shot.summary()
            }
            id--
        }
        return found
    }

    override suspend fun load(id: String): Shot {
        val response = client.get(url("/api/shots/$id")) { timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
        check(response.status.isSuccess()) { "the machine has no shot $id" }
        return GaggiuinoShotFile.parse(response.bodyAsText(), fallbackId = id)
    }

    internal fun parseLatest(text: String): Long? {
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                return text.trim().toLongOrNull()
            }
        return idIn(root)
    }

    private fun idIn(element: JsonElement): Long? =
        when (element) {
            is JsonArray -> element.firstNotNullOfOrNull(::idIn)
            is JsonObject -> element["lastShotId"]?.let(::idIn)
            is JsonPrimitive -> element.contentOrNull?.trim()?.toLongOrNull()
        }

    private suspend fun <T> runCatchingNonCancel(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val MAX_MISSES = 3

        /** A shot is a few kB; an ESP32 that takes longer than this is not going to answer. */
        const val REQUEST_TIMEOUT_MS = 8_000L
    }
}
