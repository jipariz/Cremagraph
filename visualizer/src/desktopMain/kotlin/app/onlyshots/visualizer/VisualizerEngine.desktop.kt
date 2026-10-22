package app.onlyshots.visualizer

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun visualizerEngine(): HttpClientEngineFactory<*> = CIO
