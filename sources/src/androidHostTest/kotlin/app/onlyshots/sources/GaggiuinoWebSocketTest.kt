package app.onlyshots.sources

import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.machine.gaggiuinoSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The real transport against a fake Gaggiuino speaking the release firmware's JSON. */
class GaggiuinoWebSocketTest {
    // One thread, because the assembler is not thread-safe and samples and brew events reach it
    // from two collectors. In the app that is the main dispatcher; here it has to be said out loud.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var server: EmbeddedServer<*, *>? = null
    private val client = HttpClient(ClientCIO) { install(ClientWebSockets) }

    @AfterTest fun tearDown() {
        scope.cancel()
        client.close()
        server?.stop(0, 0)
    }

    private fun startMachine(script: suspend io.ktor.server.websocket.DefaultWebSocketServerSession.() -> Unit): Int {
        val s = embeddedServer(CIO, port = 0) {
            install(WebSockets)
            routing { webSocket("/ws") { script() } }
        }.start(wait = false)
        server = s
        return runBlocking { s.engine.resolvedConnectors().first().port }
    }

    @Test fun streamsAShotIntoTheAssembler(): Unit = runBlocking {
        val port = startMachine {
            send(GaggiuinoFrames.sensor(brewActive = false))
            delay(50)
            for (i in 0..30) {
                send(Frame.Text(GaggiuinoFrames.shot(i * 100L, pressure = i / 4f, weight = i * 0.5f)))
                delay(20)
            }
            send(GaggiuinoFrames.sensor(brewActive = false))
            delay(5_000)
        }
        val source = gaggiuinoSource(client, "127.0.0.1", port)
        assertEquals(SourceKind.GAGGIUINO, source.kind)
        val assembler = LiveShotAssembler("t", SourceKind.GAGGIUINO, "Zer0", Instant.fromEpochSeconds(0))
        val ended = kotlinx.coroutines.CompletableDeferred<Unit>()
        source.brewEvents.collectSubscribed(scope) {
            assembler.onBrewEvent(it)
            if (it is BrewEvent.Ended) ended.complete(Unit)
        }
        source.live.collectSubscribed(scope) { assembler.onSample(it) }
        source.start(scope)
        withTimeout(10_000) {
            source.connection.first { it == ConnectionState.Connected }
            ended.await()
        }
        assembler.settle()
        val shot = assembler.shot()
        assertEquals(31, shot.samples.size)
        assertEquals((0..30).map { it * 100_000L }, shot.samples.map { it.tUs })
        assertEquals(3_000_000, shot.durationUs)
        assertEquals(15f, shot.finalWeight)
        assertEquals(7.5f, shot.samples.last().pressure)
    }

    @Test fun machineDroppingUsRightAwayIsBusy(): Unit = runBlocking {
        val port = startMachine { /* close immediately, like a full ESPAsyncWebServer */ }
        val source = gaggiuinoSource(client, "127.0.0.1", port)
        source.start(scope)
        val state = withTimeout(10_000) { source.connection.first { it is ConnectionState.Busy } }
        assertIs<ConnectionState.Busy>(state)
    }

    @Test fun nobodyListeningIsUnreachable(): Unit = runBlocking {
        val source = gaggiuinoSource(client, "127.0.0.1", port = 1)
        source.start(scope)
        val state = withTimeout(10_000) { source.connection.first { it is ConnectionState.Unreachable } }
        assertIs<ConnectionState.Unreachable>(state)
    }
}
