package app.onlyshots.sources

import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.machine.GaggiMateHistory
import app.onlyshots.sources.machine.createMachineHttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** [GaggiMateHistory] through the real client, against a fake machine's binary history API on localhost. */
class GaggiMateHistoryTest {
    private var server: EmbeddedServer<*, *>? = null
    private val client = createMachineHttpClient()
    private val asked = mutableListOf<String>()

    @AfterTest fun tearDown() {
        client.close()
        server?.stop(0, 0)
    }

    /**
     * A machine serving [index] at `recent.bin` (none: firmware without the API) and the shot logs in [logs], keyed by
     * the file name they are asked for.
     */
    private fun machine(index: ByteArray?, logs: Map<String, ByteArray> = emptyMap()): GaggiMateHistory {
        val s =
            embeddedServer(CIO, port = 0) {
                    routing {
                        get("/api/history/recent.bin") {
                            asked += "recent.bin?limit=" + call.request.queryParameters["limit"]
                            if (index == null) call.respondText("not found", status = HttpStatusCode.NotFound)
                            else call.respondBytes(index, ContentType.Application.OctetStream)
                        }
                        get("/api/history/{file}") {
                            val file = call.parameters["file"]!!
                            asked += file
                            val body = logs[file]
                            if (body == null) call.respondText("not found", status = HttpStatusCode.NotFound)
                            else call.respondBytes(body, ContentType.Application.OctetStream)
                        }
                    }
                }
                .start(wait = false)
        server = s
        val port = runBlocking { s.engine.resolvedConnectors().first().port }
        return GaggiMateHistory(client, "127.0.0.1", port)
    }

    @Test
    fun theIndexListsTheRecentShotsNewestFirst() = runBlocking {
        val history =
            machine(
                GaggiMateBytes.shotIndex(
                    GaggiMateBytes.entry(id = 12, name = "Londinium"),
                    GaggiMateBytes.entry(id = 11, name = "Flush", deleted = true),
                    GaggiMateBytes.entry(id = 10, name = "Gentle"),
                )
            )
        val shots = history.list(8)
        assertEquals(listOf("12", "10"), shots.map { it.id })
        assertEquals(listOf("Londinium", "Gentle"), shots.map { it.profileName })
        assertTrue(shots.all { it.source == SourceKind.GAGGIMATE })
        assertEquals(listOf("recent.bin?limit=8"), asked)
    }

    @Test
    fun theLimitIsCappedAtWhatTheFirmwareServes() = runBlocking {
        machine(GaggiMateBytes.shotIndex(GaggiMateBytes.entry(id = 1))).list(500)
        assertEquals(listOf("recent.bin?limit=50"), asked)
    }

    @Test
    fun theListNeverRunsPastTheLimitTheCallerAsked() = runBlocking {
        val history = machine(GaggiMateBytes.shotIndex(*(1..5).map { GaggiMateBytes.entry(id = it.toLong()) }.toTypedArray()))
        assertEquals(2, history.list(2).size)
    }

    @Test
    fun aShotIsLoadedByItsPaddedName() = runBlocking {
        val log = GaggiMateBytes.shotLog(profileName = "Londinium", samples = 2)
        val history = machine(index = null, logs = mapOf("000123.slog" to log))
        val shot = history.load("123")
        assertEquals("123", shot.id)
        assertEquals("Londinium", shot.profileName)
        assertEquals(2, shot.samples.size)
        assertEquals(listOf("000123.slog"), asked)
    }

    @Test
    fun aShotTheMachineDoesNotHaveFails() = runBlocking {
        val history = machine(index = null)
        assertFailsWith<IllegalStateException> { history.load("7") }
        Unit
    }

    @Test
    fun firmwareWithoutTheApiListsNothing() = runBlocking {
        assertEquals(emptyList(), machine(index = null).list(8))
    }

    @Test
    fun anIndexThatIsNotOneListsNothing() = runBlocking {
        assertEquals(emptyList(), machine("<html>404</html>".encodeToByteArray()).list(8))
    }
}
