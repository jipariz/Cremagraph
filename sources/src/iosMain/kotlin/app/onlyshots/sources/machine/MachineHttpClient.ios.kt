package app.onlyshots.sources.machine

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun machineEngine(): HttpClientEngineFactory<*> = Darwin
