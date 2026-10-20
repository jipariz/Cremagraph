package app.onlyshots.overlay

import kotlin.math.roundToInt

/**
 * Colours in the overlay engine are packed ARGB ints so the engine stays free of UI types and the same values feed
 * Compose, Android bitmaps and Core Graphics.
 */
typealias Argb = Int

fun rgb(hex: Int): Argb = (0xFF shl 24) or (hex and 0xFFFFFF)

fun rgba(r: Int, g: Int, b: Int, a: Float): Argb =
    ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

const val TRANSPARENT: Argb = 0

val Argb.alpha: Float
    get() = ((this ushr 24) and 0xFF) / 255f

/** Multiplies the alpha channel, e.g. by the overlay opacity. */
fun Argb.timesAlpha(f: Float): Argb {
    val a = (((this ushr 24) and 0xFF) * f.coerceIn(0f, 1f)).roundToInt()
    return (a shl 24) or (this and 0xFFFFFF)
}
