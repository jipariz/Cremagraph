package app.onlyshots.sources

import app.onlyshots.sources.machine.GaggiMate
import app.onlyshots.sources.machine.MdnsService
import app.onlyshots.sources.machine.createMachineHttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** [GaggiMate.identify] against a fake `/api/status` on localhost. */
class GaggiMateIdentifyTest {
    private var server: EmbeddedServer<*, *>? = null
    private val client = createMachineHttpClient()

    @AfterTest fun tearDown() {
        client.close()
        server?.stop(0, 0)
    }

    /** Started outside `runBlocking`: starting it inside one deadlocks the CIO engine. */
    private fun serve(status: HttpStatusCode = HttpStatusCode.OK, body: String): Int {
        val s =
            embeddedServer(CIO, port = 0) {
                    routing {
                        get("/api/status") { call.respondText(body, ContentType.Application.Json, status) }
                    }
                }
                .start(wait = false)
        server = s
        return runBlocking { s.engine.resolvedConnectors().first().port }
    }

    private fun identify(port: Int) = runBlocking { GaggiMate.identify(client, "127.0.0.1", port, 1_000) }

    @Test
    fun aMachineAnsweringWithItsModeAndTemperaturesIsOne() {
        assertTrue(identify(serve(body = """{"mode":1,"tt":93,"ct":92.4}""")))
    }

    @Test
    fun aStandbyMachineIsStillOne() {
        assertTrue(identify(serve(body = """{"mode":0,"tt":0,"ct":21.5}""")))
    }

    @Test
    fun someOtherJsonIsNot() {
        assertFalse(identify(serve(body = """{"status":"ok","uptime":1234}""")))
        assertFalse(identify(serve(body = """{"mode":9,"tt":93,"ct":92.4}""")))
        assertFalse(identify(serve(body = """{"mode":1,"ct":92.4}""")))
    }

    @Test
    fun aPageThatIsNotJsonIsNot() {
        assertFalse(identify(serve(body = "<html><body>router</body></html>")))
    }

    @Test
    fun anErrorIsNot() {
        assertFalse(identify(serve(status = HttpStatusCode.NotFound, body = "not found")))
    }

    @Test
    fun nothingListeningIsNot() {
        assertFalse(identify(1))
    }

    @Test
    fun onlyAServiceNamedLikeTheMachineIsWorthProbing() {
        assertTrue(GaggiMate.matchesService(MdnsService("GaggiMate-A1B2", "_http._tcp.", "192.168.1.9", 80)))
        assertTrue(GaggiMate.matchesService(MdnsService("gaggimate", "_http._tcp.", "192.168.1.9", 80)))
        assertFalse(GaggiMate.matchesService(MdnsService("gaggiuino", "_http._tcp.", "192.168.1.9", 80)))
    }
}
