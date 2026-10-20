package app.onlyshots.style

import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.style_swatch_apricot
import app.onlyshots.core.resources.style_swatch_blue
import app.onlyshots.core.resources.style_swatch_clear
import app.onlyshots.core.resources.style_swatch_cobalt
import app.onlyshots.core.resources.style_swatch_ember
import app.onlyshots.core.resources.style_swatch_espresso
import app.onlyshots.core.resources.style_swatch_forest
import app.onlyshots.core.resources.style_swatch_graphite
import app.onlyshots.core.resources.style_swatch_honey
import app.onlyshots.core.resources.style_swatch_ice_blue
import app.onlyshots.core.resources.style_swatch_lilac
import app.onlyshots.core.resources.style_swatch_milk
import app.onlyshots.core.resources.style_swatch_mint
import app.onlyshots.core.resources.style_swatch_oxblood
import app.onlyshots.core.resources.style_swatch_rose
import app.onlyshots.core.resources.style_swatch_sky
import app.onlyshots.core.resources.style_swatch_teal
import app.onlyshots.core.resources.style_swatch_white
import app.onlyshots.core.resources.text
import app.onlyshots.overlay.Argb
import app.onlyshots.overlay.alpha
import app.onlyshots.overlay.rgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource

/** A named colour of the picker's presets: colours that read on video. */
data class Swatch(val name: StringResource, val color: Argb)

/** Figma 13 · F's presets, and the panel colours the built-ins and the editor's examples use. */
val Swatches =
    listOf(
        Swatch(Res.string.style_swatch_ice_blue, rgb(0x7FB8FF)),
        Swatch(Res.string.style_swatch_blue, rgb(0x3A90DA)),
        Swatch(Res.string.style_swatch_mint, rgb(0x1FC48C)),
        Swatch(Res.string.style_swatch_honey, rgb(0xFFC94D)),
        Swatch(Res.string.style_swatch_apricot, rgb(0xFF9A6B)),
        Swatch(Res.string.style_swatch_ember, rgb(0xF05A3C)),
        Swatch(Res.string.style_swatch_rose, rgb(0xE07F78)),
        Swatch(Res.string.style_swatch_lilac, rgb(0xC4A8FF)),
        Swatch(Res.string.style_swatch_milk, rgb(0xF2E6DC)),
        Swatch(Res.string.style_swatch_white, rgb(0xFFFFFF)),
        Swatch(Res.string.style_swatch_teal, rgb(0x5CC4B8)),
        Swatch(Res.string.style_swatch_sky, rgb(0xA8C8FF)),
    )

/** Panel colours: dark and deep, for under the curves. */
val PanelSwatches =
    listOf(
        Swatch(Res.string.style_swatch_espresso, rgb(0x2B1A12)),
        Swatch(Res.string.style_swatch_oxblood, rgb(0x4A1216)),
        Swatch(Res.string.style_swatch_graphite, rgb(0x121416)),
        Swatch(Res.string.style_swatch_cobalt, rgb(0x0050EF)),
        Swatch(Res.string.style_swatch_forest, rgb(0x0F3D2A)),
        Swatch(Res.string.style_swatch_clear, rgb(0xFFFFFF)),
    )

/** The colour's name when it is a preset, else its hex. */
fun colorName(color: Argb): UiText {
    val opaque = color or 0xFF000000.toInt()
    return (Swatches + PanelSwatches).firstOrNull { it.color == opaque }?.name?.text() ?: hex(color).raw()
}

/** "#7FB8FF" — the colour without its alpha. */
fun hex(color: Argb): String = "#" + (color and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** Parses "#7FB8FF" or "7fb8ff"; null when it is not a colour. */
fun parseHex(text: String): Argb? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toIntOrNull(16)?.let { rgb(it) }
}

/** "#7FB8FF80": the colour with its alpha last, as CSS writes it. */
fun hexWithAlpha(color: Argb): String =
    hex(color) + ((color ushr 24) and 0xFF).toString(16).uppercase().padStart(2, '0')

/** Parses "#7FB8FF80" (alpha last) or a plain "#7FB8FF" (opaque); null when it is not a colour. */
fun parseHexWithAlpha(text: String): Argb? {
    val t = text.trim().removePrefix("#")
    if (t.length == 6) return parseHex(t)
    if (t.length != 8) return null
    val rgb = t.take(6).toIntOrNull(16) ?: return null
    val a = t.drop(6).toIntOrNull(16) ?: return null
    return (a shl 24) or rgb
}

/** "clear · 14 %": a colour with its opacity, as the editor's colour chips show a tint. */
fun tintLabel(color: Argb, opacity: Float): UiText =
    UiText.Joined(listOf(colorName(color), "${(opacity * 100).roundToInt()} %".raw()))

/** Hue 0..360, saturation and value 0..1. */
fun Argb.toHsv(): Triple<Float, Float, Float> {
    val r = ((this ushr 16) and 0xFF) / 255f
    val g = ((this ushr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val h =
        when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d).mod(6f))
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
    return Triple(h, if (mx == 0f) 0f else d / mx, mx)
}

fun hsv(h: Float, s: Float, v: Float, alpha: Float = 1f): Argb {
    val c = v * s
    val x = c * (1 - abs((h / 60f).mod(2f) - 1))
    val m = v - c
    val (r, g, b) =
        when ((h.mod(360f) / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return ((alpha * 255f).roundToInt() shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** The colour at [alpha]. */
fun Argb.withAlpha(alpha: Float): Argb = ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (this and 0xFFFFFF)

/** Opacity of the colour, 0..1. */
val Argb.opacity: Float
    get() = alpha
