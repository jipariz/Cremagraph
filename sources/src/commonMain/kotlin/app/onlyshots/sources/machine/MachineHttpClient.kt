package app.onlyshots.sources.machine

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets

/** OkHttp on Android, Darwin (NSURLSession) on iOS. */
internal expect fun machineEngine(): HttpClientEngineFactory<*>

/** The one HTTP client for the user's machine or bridge on the local network. No other hosts. */
fun createMachineHttpClient(): HttpClient =
    HttpClient(machineEngine()) {
        install(WebSockets) { pingIntervalMillis = 5_000 }
        install(HttpTimeout) { connectTimeoutMillis = 3_000 }
    }
