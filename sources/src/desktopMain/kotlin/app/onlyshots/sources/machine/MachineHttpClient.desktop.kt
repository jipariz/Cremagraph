package app.onlyshots.sources.machine

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun machineEngine(): HttpClientEngineFactory<*> = CIO
