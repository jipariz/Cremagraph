package app.onlyshots.sources

import app.onlyshots.sources.machine.GaggiuinoHistory
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** [GaggiuinoHistory] through the real client, against a fake machine's HTTP API on localhost. */
class GaggiuinoHistoryTest {
    private var server: EmbeddedServer<*, *>? = null
    private val client = createMachineHttpClient()

    @AfterTest fun tearDown() {
        client.close()
        server?.stop(0, 0)
    }

    /**
     * A machine that names its newest shot as [latestBody] (none: firmware without the API) and holds the shots in
     * [shots].
     */
    private fun machine(latestBody: String?, shots: Set<Long> = emptySet()): GaggiuinoHistory {
        val s = embeddedServer(CIO, port = 0) {
            routing {
                if (latestBody != null) {
                    get("/api/shots/latest") { call.respondText(latestBody, ContentType.Application.Json) }
                }
                get("/api/shots/{id}") {
                    val id = call.parameters["id"]!!.toLong()
                    if (id in shots) call.respondText(shot(id), ContentType.Application.Json)
                    else call.respondText("not found", status = HttpStatusCode.NotFound)
                }
            }
        }.start(wait = false)
        server = s
        val port = runBlocking { s.engine.resolvedConnectors().first().port }
        return GaggiuinoHistory(client, "127.0.0.1", port)
    }

    @Test fun theNewestIdIsReadInEachShape() {
        val history = GaggiuinoHistory(client, "127.0.0.1")
        assertEquals(121L, history.parseLatest("""[{"lastShotId":"121"}]"""))
        assertEquals(121L, history.parseLatest("""{"lastShotId":121}"""))
        assertEquals(121L, history.parseLatest("121"))
        assertNull(history.parseLatest("""{"something":"else"}"""))
    }

    @Test fun theListCountsDownFromTheNewestAndSkipsADeletedShot() {
        val history = machine("""[{"lastShotId":"12"}]""", shots = setOf(12, 10, 9, 8))
        val list = runBlocking { history.list(3) }
        assertEquals(listOf("12", "10", "9"), list.map { it.id })
        assertEquals("Shot 10", list[1].profileName)
    }

    @Test fun threeMissesInARowEndTheList() {
        val history = machine("""{"lastShotId":20}""", shots = setOf(20, 15))
        assertEquals(listOf("20"), runBlocking { history.list(10) }.map { it.id })
    }

    @Test fun aShotIsLoadedWhole() {
        val history = machine("7", shots = setOf(7))
        val shot = runBlocking { history.load("7") }
        assertEquals("7", shot.id)
        assertEquals(3, shot.samples.size)
    }

    @Test fun firmwareWithoutTheApiListsNothing() {
        val history = machine(latestBody = null)
        assertEquals(emptyList(), runBlocking { history.list(10) })
    }

    private fun shot(id: Long) =
        """
        {
          "id": $id,
          "timestamp": ${1_760_000_000 + id * 600},
          "duration": 30,
          "profileName": "Shot $id",
          "datapoints": {
            "timeInShot": [0, 10, 20],
            "pressure": [0, 60, 90],
            "pumpFlow": [0, 25, 18],
            "shotWeight": [0, 14, 360],
            "temperature": [930, 931, 928]
          }
        }
        """
            .trimIndent()
}
