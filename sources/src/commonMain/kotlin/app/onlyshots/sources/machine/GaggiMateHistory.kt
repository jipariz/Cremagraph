package app.onlyshots.sources.machine

import app.onlyshots.core.model.GaggiMateShotLog
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess

/**
 * A GaggiMate's own shot history, over its HTTP API: `GET /api/history/recent.bin` is the newest shots as a binary
 * index and `GET /api/history/<id>.slog` one shot's readings, both read by [GaggiMateShotLog]. Unlike Gaggiuino's, this
 * list comes in one request, so nothing has to be guessed at by counting ids down.
 *
 * Endpoints from `src/display/plugins/WebUIPlugin.cpp` in `jniebuhr/gaggimate` (master
 * `b78beee4dbcc1cd59946256f05e5cafa5450ec60`). The firmware caps `limit` at 50 and serves the index newest first.
 */
class GaggiMateHistory(private val client: HttpClient, private val host: String, private val port: Int = 80) :
    MachineHistory {
    private fun url(path: String) = "http://$host:$port$path"

    override suspend fun list(limit: Int): List<ShotSummary> {
        val want = limit.coerceIn(1, MAX_LIMIT)
        val response =
            client.get(url("/api/history/recent.bin?limit=$want")) {
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            }
        if (!response.status.isSuccess()) return emptyList()
        return GaggiMateShotLog.index(response.readRawBytes()).take(limit)
    }

    override suspend fun load(id: String): Shot {
        val response =
            client.get(url("/api/history/" + GaggiMateShotLog.fileName(id))) {
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            }
        check(response.status.isSuccess()) { "the machine has no shot $id" }
        return GaggiMateShotLog.parse(response.readRawBytes(), fallbackId = id)
    }

    private companion object {
        /** `recent.bin` clamps to this upstream; asking for more is silently the same request. */
        const val MAX_LIMIT = 50

        /** A shot is a few kB; an ESP32 that takes longer than this is not going to answer. */
        const val REQUEST_TIMEOUT_MS = 8_000L
    }
}
