package app.onlyshots.overlay

import kotlin.math.pow

/*
 * WCAG contrast, for the overlay's readability: the style editor's guard, Minimal's switch to its dark set and the
 * scrim behind busy frames all ask the same question — how well does this colour read over that background?
 */

/** WCAG relative luminance of an opaque colour, 0 (black) … 1 (white). */
fun relativeLuminance(color: Argb): Float {
    fun channel(c: Int): Float {
        val s = c / 255f
        return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
    }
    val r = channel((color ushr 16) and 0xFF)
    val g = channel((color ushr 8) and 0xFF)
    val b = channel(color and 0xFF)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/** WCAG contrast ratio between two luminances, 1 … 21. */
fun contrastRatio(a: Float, b: Float): Float {
    val hi = maxOf(a, b)
    val lo = minOf(a, b)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** Luminance of [over] laid at its alpha onto a background of luminance [under]: what the eye gets behind text. */
fun luminanceOver(over: Argb, under: Float): Float {
    val a = over.alpha
    return relativeLuminance(over) * a + under * (1f - a)
}

/** Contrast of [text] (its alpha blended in) over a background of luminance [background]. */
fun contrastOf(text: Argb, background: Float): Float = contrastRatio(luminanceOver(text, background), background)

/** Values must read at 4.5 : 1, labels at 3 : 1 (WCAG AA for normal and large text). */
const val VALUE_CONTRAST = 4.5f
const val LABEL_CONTRAST = 3f
