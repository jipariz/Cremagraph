package app.onlyshots.visualizer

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun visualizerEngine(): HttpClientEngineFactory<*> = Darwin
