package app.onlyshots.sources

import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.machine.gaggiMateSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The real transport against a fake GaggiMate speaking `evt:status`. */
class GaggiMateWebSocketTest {
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

    /** Started outside `runBlocking`: starting it inside one deadlocks the CIO engine. */
    private fun startMachine(script: suspend io.ktor.server.websocket.DefaultWebSocketServerSession.() -> Unit): Int {
        val s =
            embeddedServer(CIO, port = 0) {
                    install(WebSockets)
                    routing { webSocket("/ws") { script() } }
                }
                .start(wait = false)
        server = s
        return runBlocking { s.engine.resolvedConnectors().first().port }
    }

    private suspend fun recordShot(source: WebSocketShotSourceHolder): LiveShotAssembler {
        val assembler = LiveShotAssembler("t", SourceKind.GAGGIMATE, "GaggiMate", Instant.fromEpochSeconds(0))
        val ended = CompletableDeferred<Unit>()
        source.value.brewEvents.collectSubscribed(scope) {
            assembler.onBrewEvent(it)
            if (it is BrewEvent.Ended) ended.complete(Unit)
        }
        source.value.live.collectSubscribed(scope) { assembler.onSample(it) }
        source.value.start(scope)
        withTimeout(10_000) {
            source.value.connection.first { it == ConnectionState.Connected }
            ended.await()
        }
        return assembler
    }

    @Test
    fun streamsAShotIntoTheAssembler(): Unit = runBlocking {
        val port = startMachine {
            // As the firmware does: the slow state in full the moment a client connects, then telemetry.
            send(GaggiMateFrames.state())
            send(GaggiMateFrames.telemetry())
            delay(50)
            for (i in 0..29) {
                val phase = if (i < 8) "Pre-infusion" else "Pour"
                send(
                    Frame.Text(
                        GaggiMateFrames.telemetry(
                            process = GaggiMateFrames.brew(i * 100L, phase = phase),
                            pressure = i / 4f,
                            weight = i * 0.5f,
                        )
                    )
                )
                delay(20)
            }
            send(
                Frame.Text(
                    GaggiMateFrames.telemetry(
                        process = GaggiMateFrames.brew(3_000, active = false, phase = "Pour"),
                        pressure = 7.5f,
                        weight = 15f,
                    )
                )
            )
            delay(5_000)
        }
        val source = gaggiMateSource(client, "127.0.0.1", port)
        assertEquals(SourceKind.GAGGIMATE, source.kind)
        val assembler = recordShot(WebSocketShotSourceHolder(source))
        assembler.settle()
        val shot = assembler.shot()
        // Thirty while brewing, plus the last one the finished frame carries.
        assertEquals(31, shot.samples.size)
        assertEquals((0..29).map { it * 100_000L } + 3_000_000L, shot.samples.map { it.tUs })
        assertEquals(3_000_000, shot.durationUs)
        assertEquals(15f, shot.finalWeight)
        assertEquals(7.5f, shot.samples.last().pressure)
        // Pre-infusion then Pour, numbered in the order they showed.
        assertEquals(listOf(0L, 800_000L), shot.phaseStartsUs)
    }

    @Test
    fun aStandardMachineRecordsEverythingButPressure(): Unit = runBlocking {
        val port = startMachine {
            send(GaggiMateFrames.state(hasPressure = false))
            delay(50)
            for (i in 0..10) {
                send(Frame.Text(GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(i * 100L), pressure = 0f)))
                delay(20)
            }
            send(
                Frame.Text(
                    GaggiMateFrames.telemetry(process = GaggiMateFrames.brew(1_000, active = false), pressure = 0f)
                )
            )
            delay(5_000)
        }
        val assembler = recordShot(WebSocketShotSourceHolder(gaggiMateSource(client, "127.0.0.1", port)))
        assembler.settle()
        val shot = assembler.shot()
        assertNull(shot.samples.first().pressure)
        assertEquals(93.1f, shot.samples.first().temperature)
    }

    @Test
    fun machineDroppingUsRightAwayIsBusy(): Unit = runBlocking {
        val port = startMachine { /* close immediately, like a full ESPAsyncWebServer */ }
        val source = gaggiMateSource(client, "127.0.0.1", port)
        source.start(scope)
        assertIs<ConnectionState.Busy>(withTimeout(10_000) { source.connection.first { it is ConnectionState.Busy } })
    }

    @Test
    fun nobodyListeningIsUnreachable(): Unit = runBlocking {
        val source = gaggiMateSource(client, "127.0.0.1", port = 1)
        source.start(scope)
        assertIs<ConnectionState.Unreachable>(
            withTimeout(10_000) { source.connection.first { it is ConnectionState.Unreachable } }
        )
    }
}

/** Only so the helper above can take the source without naming its type twice. */
internal class WebSocketShotSourceHolder(val value: app.onlyshots.sources.machine.WebSocketShotSource)
