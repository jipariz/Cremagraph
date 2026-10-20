@file:JsModule("./skiko.mjs")

package app.onlyshots.video

/** Skiko's own wasm module: its exports (`_`) include the memory Skia's pixels live in. */
internal external val loadedWasm: JsAny

/** Skiko's Emscripten GL: WebGL contexts that Skia can draw into. */
internal external val GL: JsAny
