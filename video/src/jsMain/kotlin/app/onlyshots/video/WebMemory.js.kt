package app.onlyshots.video

// Kotlin/JS arrays are JS typed arrays already: a ByteArray is an Int8Array and a FloatArray a Float32Array, so each
// crossing is a single typed-array copy.

internal actual fun bytesOf(array: JsAny): ByteArray =
    js("new Int8Array(array.buffer, array.byteOffset, array.byteLength).slice()")

internal actual fun floatsOf(array: JsAny): FloatArray = js("Float32Array.from(array)")

internal actual fun floatsToJs(floats: FloatArray): JsAny = js("Float32Array.from(floats)")

internal actual fun skiaMemory(): JsAny = skiaMemoryOf(loadedWasm)

internal actual fun skikoGl(): JsAny = GL

private fun skiaMemoryOf(skiko: JsAny): JsAny = js("skiko._.memory.buffer")
