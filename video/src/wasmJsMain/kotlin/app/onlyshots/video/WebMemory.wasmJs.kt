@file:OptIn(UnsafeWasmMemoryApi::class)

package app.onlyshots.video

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

// Typed arrays cross through Kotlin's own wasm memory: one bulk copy each way rather than one call per element.

internal actual fun bytesOf(array: JsAny): ByteArray {
    val n = byteLength(array)
    if (n == 0) return ByteArray(0)
    return withScopedMemoryAllocator { a ->
        val p = a.allocate(n)
        copyIntoMemory(wasmMemory, array, p.address.toInt())
        ByteArray(n) { (p + it).loadByte() }
    }
}

internal actual fun floatsOf(array: JsAny): FloatArray {
    val n = floatLength(array)
    if (n == 0) return FloatArray(0)
    return withScopedMemoryAllocator { a ->
        val p = a.allocate(n * 4)
        copyFloatsIntoMemory(wasmMemory, array, p.address.toInt())
        FloatArray(n) { Float.fromBits((p + it * 4).loadInt()) }
    }
}

internal actual fun floatsToJs(floats: FloatArray): JsAny = withScopedMemoryAllocator { a ->
    val p = a.allocate(floats.size * 4)
    for (i in floats.indices) (p + i * 4).storeInt(floats[i].toRawBits())
    floatsFromMemory(wasmMemory, p.address.toInt(), floats.size)
}

private fun byteLength(array: JsAny): Int = js("array.byteLength")

private fun floatLength(array: JsAny): Int = js("array.length")

// The Kotlin/wasm module's own memory, from wasmMemory: its buffer is read on every use, since growing replaces it.
private fun copyIntoMemory(memory: JsAny, array: JsAny, address: Int): Unit =
    js(
        "new Uint8Array(memory.buffer, address, array.byteLength).set(new Uint8Array(array.buffer, array.byteOffset, array.byteLength))"
    )

private fun copyFloatsIntoMemory(memory: JsAny, array: JsAny, address: Int): Unit =
    js("new Float32Array(memory.buffer, address, array.length).set(array)")

private fun floatsFromMemory(memory: JsAny, address: Int, length: Int): JsAny =
    js("new Float32Array(memory.buffer.slice(address, address + length * 4))")

internal actual fun skiaMemory(): JsAny = skiaMemoryOf(loadedWasm)

internal actual fun skikoGl(): JsAny = GL

private fun skiaMemoryOf(skiko: JsAny): JsAny = js("skiko._.memory.buffer")
