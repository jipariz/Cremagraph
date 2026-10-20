package app.onlyshots.overlay

import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.Material
import app.onlyshots.overlay.scene.ADAPT_BRIGHT

/** A reading whose values would be hard to read over this video, and how hard. */
data class ReadabilityIssue(val reading: Reading, val value: Argb, val ratio: Float)

/** Luminance of what lies behind the panel's text: the panel's own colour laid over the video at [video]. */
private fun GraphStyle.panelLuminance(video: Float): Float {
    val p = panel
    val tiles = p.tiles
    return when {
        p.material == Material.NONE -> video
        p.gradient != null -> luminanceOver(p.gradient!![p.gradient!!.size / 2].color, video)
        tiles != null -> luminanceOver(p.color.timesAlpha(p.opacity), luminanceOver(tiles.base, video))
        else -> luminanceOver(p.color.timesAlpha(p.opacity), video)
    }
}

/** Luminance behind the value of [reading]: its cell over the panel over the video. */
private fun GraphStyle.cellLuminance(reading: Reading, video: Float): Float {
    val under = panelLuminance(video)
    return when (cells.look) {
        CellLook.FILLED -> luminanceOver(cells.fill, under)
        CellLook.GLOSSY -> cells.gradient?.let { luminanceOver(it[it.size / 2].color, under) } ?: under
        CellLook.TILES -> {
            val base = panel.tiles?.base ?: 0
            luminanceOver((cells.tiles[reading] ?: panel.color).timesAlpha(panel.opacity), luminanceOver(base, video))
        }
        CellLook.OUTLINED,
        CellLook.PLAIN -> under
    }
}

/**
 * The reading that reads worst over video of luminance [video], if it is below [VALUE_CONTRAST]. A style that adapts to
 * the video is checked with the set it would show; a halo or drop shadow under the text counts as enough at
 * [LABEL_CONTRAST], as the halo carries the rest.
 */
fun GraphStyle.readabilityIssue(video: Float): ReadabilityIssue? {
    val dark = adapt.dark?.takeIf { adapt.enabled && video > ADAPT_BRIGHT }
    val needed = if (text.effect != null) LABEL_CONTRAST else VALUE_CONTRAST
    return Reading.entries
        .map { r ->
            val value = dark?.text ?: cells.values[r] ?: text.title
            ReadabilityIssue(r, value, contrastOf(value, cellLuminance(r, video)))
        }
        .filter { it.ratio < needed }
        .minByOrNull { it.ratio }
}

/**
 * "Fix it for me": a style that reads at [VALUE_CONTRAST] over video of luminance [video]. No panel → it adapts to the
 * video with Minimal's dark set; otherwise the panel (and filled cells) darken and thicken step by step.
 */
fun GraphStyle.fixReadability(video: Float): GraphStyle {
    if (panel.material == Material.NONE) {
        return copy(adapt = adapt.copy(enabled = true, dark = adapt.dark ?: GraphStyles.Minimal.adapt.dark))
    }
    var s = this
    repeat(12) {
        if (s.readabilityIssue(video) == null) return s
        val p = s.panel
        s =
            s.copy(
                panel =
                    p.copy(
                        color = p.color.towardBlack(0.25f),
                        opacity = (p.opacity + 0.1f).coerceAtMost(1f),
                        gradient = p.gradient?.map { it.copy(color = it.color.towardBlack(0.25f)) },
                    ),
                cells =
                    if (s.cells.look == CellLook.FILLED) s.cells.copy(fill = s.cells.fill.towardBlack(0.25f))
                    else s.cells,
            )
    }
    return s
}

/** The colour [f] of the way to black, alpha kept. */
fun Argb.towardBlack(f: Float): Argb {
    fun ch(shift: Int) = ((((this ushr shift) and 0xFF) * (1f - f)).toInt() and 0xFF) shl shift
    return (this and 0xFF000000.toInt()) or ch(16) or ch(8) or ch(0)
}
