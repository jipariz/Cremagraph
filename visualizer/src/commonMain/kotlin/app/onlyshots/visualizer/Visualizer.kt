package app.onlyshots.visualizer

import app.onlyshots.core.model.NotAShotFile
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotFiles
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where a shot on visualizer.coffee is: the four letters its owner got from "Share" (valid for about an hour), or the
 * shot itself, by its link (`https://visualizer.coffee/shots/{id}`) or bare id (public shots, no expiry).
 */
sealed interface VisualizerRef {
    data class Code(val code: String) : VisualizerRef

    data class Id(val id: String) : VisualizerRef

    companion object {
        private val CODE = Regex("^[A-Za-z]{4}$")
        private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** What the user typed or pasted, or null when it is neither a code nor a shot's link. */
        fun parse(input: String): VisualizerRef? {
            val text = input.trim()
            if (CODE.matches(text)) return Code(text.uppercase())
            return UUID.find(text)?.let { Id(it.value.lowercase()) }
        }
    }
}

/** Why a shot could not be fetched. Meaning only: the screens word it. */
enum class VisualizerError {
    /** No shot by that code or id: mistyped, or the code has expired. */
    NOT_FOUND,
    /** The visualizer's rate limit: try again in a minute. */
    BUSY,
    /** No connection, or the visualizer did not answer. */
    UNREACHABLE,
    /** It answered with something that is not a shot this app can draw. */
    NOT_A_SHOT,
}

class VisualizerException(val error: VisualizerError) : Exception(error.name)

/** Fetches shots from visualizer.coffee. Throws [VisualizerException]. */
interface Visualizer {
    suspend fun fetch(ref: VisualizerRef): Shot
}

/**
 * The visualizer's public API: no account is needed to read a shared or public shot. A shared shot leaves its readings
 * out unless asked for them (`with_data=1`); a shot by id carries them.
 *
 * ponytail: endpoints from `config/routes.rb` and `app/controllers/api/shots_controller.rb` of miharekar/visualizer at
 * commit 4048586935aa28c083c73ad46af65e752900161e. Rate limit 50 requests a minute per address, answered with 429.
 */
class VisualizerClient(private val client: HttpClient, private val base: String = BASE) : Visualizer {
    override suspend fun fetch(ref: VisualizerRef): Shot {
        val response =
            try {
                when (ref) {
                    is VisualizerRef.Code ->
                        client.get("$base/api/shots/shared") {
                            parameter("code", ref.code)
                            parameter("with_data", 1)
                        }
                    is VisualizerRef.Id -> client.get("$base/api/shots/${ref.id}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw VisualizerException(VisualizerError.UNREACHABLE)
            }
        return read(response, ref)
    }

    private suspend fun read(response: HttpResponse, ref: VisualizerRef): Shot {
        when (response.status.value) {
            in 200..299 -> Unit
            404 -> throw VisualizerException(VisualizerError.NOT_FOUND)
            429 -> throw VisualizerException(VisualizerError.BUSY)
            else -> throw VisualizerException(VisualizerError.UNREACHABLE)
        }
        val fallbackId =
            when (ref) {
                is VisualizerRef.Code -> "visualizer-${ref.code}"
                is VisualizerRef.Id -> "visualizer-${ref.id}"
            }
        return try {
            ShotFiles.parse(response.bodyAsText(), fallbackId)
        } catch (e: NotAShotFile) {
            throw VisualizerException(VisualizerError.NOT_A_SHOT)
        }
    }

    companion object {
        const val BASE = "https://visualizer.coffee"
    }
}

/** The visualizer.coffee the app talks to, on its own client. */
fun createVisualizer(): Visualizer = VisualizerClient(createVisualizerHttpClient())

/** OkHttp on Android, Darwin on iOS, CIO on the desktop, the browser's `fetch` on the web. */
internal expect fun visualizerEngine(): HttpClientEngineFactory<*>

/** The HTTP client for visualizer.coffee only; the machine has its own, for the local network. */
fun createVisualizerHttpClient(): HttpClient =
    HttpClient(visualizerEngine()) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
    }
