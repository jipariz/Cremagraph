package app.onlyshots.sources.machine

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun machineEngine(): HttpClientEngineFactory<*> = OkHttp
