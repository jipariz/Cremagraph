package app.onlyshots.sources

import app.onlyshots.sources.machine.Gaggiuino
import app.onlyshots.sources.machine.createMachineHttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.send
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** [Gaggiuino.identify] through the real client, against fake machines on localhost. */
class GaggiuinoIdentifyTest {
    private var server: EmbeddedServer<*, *>? = null
    private val client = createMachineHttpClient()

    @AfterTest fun tearDown() {
        client.close()
        server?.stop(0, 0)
    }

    private fun serve(script: suspend DefaultWebSocketServerSession.() -> Unit): Int {
        val s = embeddedServer(CIO, port = 0) {
            install(WebSockets)
            routing { webSocket("/ws") { script() } }
        }.start(wait = false)
        server = s
        return runBlocking { s.engine.resolvedConnectors().first().port }
    }

    private fun identify(port: Int) = runBlocking { Gaggiuino.identify(client, "127.0.0.1", port, 1_000) }

    @Test fun anIdleGaggiuinoIsRecognisedByItsSensorUpdate() {
        val port = serve {
            send(GaggiuinoFrames.LOG)
            delay(300)
            send(GaggiuinoFrames.sensor(brewActive = false))
            awaitCancellation()
        }
        assertTrue(identify(port))
    }

    @Test fun newerFirmwareIsRecognisedByItsBinaryFrames() {
        val port = serve {
            send(io.ktor.websocket.Frame.Binary(true, GaggiuinoBinaryFrames.idleSnapshot))
            awaitCancellation()
        }
        assertTrue(identify(port))
    }

    @Test fun anotherWebSocketServerIsNot() {
        val port = serve {
            send("""{"tp":"evt:status","ct":93.1}""")
            awaitCancellation()
        }
        assertFalse(identify(port))
    }

    @Test fun aServerThatHangsUpIsNot() {
        assertFalse(identify(serve {}))
    }

    @Test fun nothingListeningIsNot() {
        assertFalse(identify(1))
    }
}
