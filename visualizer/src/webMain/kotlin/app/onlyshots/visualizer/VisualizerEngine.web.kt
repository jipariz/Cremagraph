package app.onlyshots.visualizer

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

/** The browser's `fetch`; the visualizer answers any origin (`Access-Control-Allow-Origin: *`). */
internal actual fun visualizerEngine(): HttpClientEngineFactory<*> = Js
