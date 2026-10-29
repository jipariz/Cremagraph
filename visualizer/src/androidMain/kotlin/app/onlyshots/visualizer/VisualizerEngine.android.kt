package app.onlyshots.visualizer

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun visualizerEngine(): HttpClientEngineFactory<*> = OkHttp
