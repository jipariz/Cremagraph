@file:JsModule("./skiko.mjs")
@file:JsNonModule

package app.onlyshots.video

// The JS build runs Skia in the same skiko wasm module as the wasm build, loaded through the same skiko.mjs.

/** Skiko's own wasm module: its exports (`_`) include the memory Skia's pixels live in. */
internal external val loadedWasm: JsAny

/** Skiko's Emscripten GL: WebGL contexts that Skia can draw into. */
internal external val GL: JsAny
