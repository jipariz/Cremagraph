package app.onlyshots.video

// What differs between the wasm and the JS build of the web editor: how bytes cross between Kotlin and JS, and how
// skiko's module (SkikoMemory.kt in each) is imported. Both builds run Skia in skiko's wasm module, from skiko.mjs.

/** A JS typed array's bytes, in one copy. */
internal expect fun bytesOf(array: JsAny): ByteArray

/** A JS Float32Array's values, in one copy. */
internal expect fun floatsOf(array: JsAny): FloatArray

/** [floats] as a new JS Float32Array. */
internal expect fun floatsToJs(floats: FloatArray): JsAny

/** Skia's memory as it is now: it can grow, which replaces the buffer, so it is fetched for every use. */
internal expect fun skiaMemory(): JsAny

/** Skiko's Emscripten GL: WebGL contexts that Skia can draw into. */
internal expect fun skikoGl(): JsAny
