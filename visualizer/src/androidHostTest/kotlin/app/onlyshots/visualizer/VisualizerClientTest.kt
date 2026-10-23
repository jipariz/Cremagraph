package app.onlyshots.visualizer

import app.onlyshots.core.model.SourceKind
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

/** [VisualizerClient] through the real client, against a fake visualizer on localhost. */
class VisualizerClientTest {
    private var server: EmbeddedServer<*, *>? = null
    private val client = createVisualizerHttpClient()

    @AfterTest fun tearDown() {
        client.close()
        server?.stop(0, 0)
    }

    private val shot =
        """
        { "id": "832f7109-46d5-47f5-86bc-5349de2957d8", "profile_title": "Zer0", "duration": 1.0,
          "timeframe": [0.0, 1.0], "data": { "espresso_pressure": [0.0, 9.0] } }
        """

    /** Started outside any `runBlocking`, so the server's own threads are free to answer. */
    private fun visualizer(): VisualizerClient {
        val s = embeddedServer(CIO, port = 0) {
            routing {
                get("/api/shots/shared") {
                    val code = call.request.queryParameters["code"]
                    when {
                        code == "BUSY" -> call.respondText("{}", status = HttpStatusCode.TooManyRequests)
                        code != "ABCD" ->
                            call.respondText(
                                """{"error":"Shared shot not found"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.NotFound,
                            )
                        // Without with_data the visualizer leaves the readings out.
                        call.request.queryParameters["with_data"] != "1" ->
                            call.respondText("""{"id":"x","profile_title":"Zer0"}""", ContentType.Application.Json)
                        else -> call.respondText(shot, ContentType.Application.Json)
                    }
                }
                get("/api/shots/{id}") {
                    if (call.parameters["id"] == "832f7109-46d5-47f5-86bc-5349de2957d8")
                        call.respondText(shot, ContentType.Application.Json)
                    else if (call.parameters["id"] == "00000000-0000-0000-0000-000000000000")
                        call.respondText("""{"brewdata":{"brewFlow":{}}}""", ContentType.Application.Json)
                    else call.respondText("{}", status = HttpStatusCode.NotFound)
                }
            }
        }.start(wait = false)
        server = s
        val port = runBlocking { s.engine.resolvedConnectors().first().port }
        return VisualizerClient(client, "http://127.0.0.1:$port")
    }

    @Test fun aShareCodeFetchesTheShotWithItsReadings() {
        val visualizer = visualizer()
        val fetched = runBlocking { visualizer.fetch(VisualizerRef.Code("ABCD")) }

        assertEquals("visualizer-832f7109-46d5-47f5-86bc-5349de2957d8", fetched.id)
        assertEquals(SourceKind.VISUALIZER, fetched.source)
        assertEquals(listOf(0f, 9f), fetched.samples.map { it.pressure })
    }

    @Test fun aLinkFetchesTheShot() {
        val visualizer = visualizer()
        val fetched = runBlocking { visualizer.fetch(VisualizerRef.Id("832f7109-46d5-47f5-86bc-5349de2957d8")) }

        assertEquals("Zer0", fetched.profileName)
    }

    @Test fun anExpiredCodeIsNotFound() {
        val visualizer = visualizer()
        val e = assertFailsWith<VisualizerException> { runBlocking { visualizer.fetch(VisualizerRef.Code("ZZZZ")) } }

        assertEquals(VisualizerError.NOT_FOUND, e.error)
    }

    @Test fun theRateLimitIsBusy() {
        val visualizer = visualizer()
        val e = assertFailsWith<VisualizerException> { runBlocking { visualizer.fetch(VisualizerRef.Code("BUSY")) } }

        assertEquals(VisualizerError.BUSY, e.error)
    }

    @Test fun somethingThatDrawsNothingIsNotAShot() {
        val visualizer = visualizer()
        val e = assertFailsWith<VisualizerException> {
            runBlocking { visualizer.fetch(VisualizerRef.Id("00000000-0000-0000-0000-000000000000")) }
        }

        assertEquals(VisualizerError.NOT_A_SHOT, e.error)
    }

    @Test fun noServerIsUnreachable() {
        val visualizer = VisualizerClient(client, "http://127.0.0.1:1")
        val e = assertFailsWith<VisualizerException> { runBlocking { visualizer.fetch(VisualizerRef.Code("ABCD")) } }

        assertEquals(VisualizerError.UNREACHABLE, e.error)
    }
}
