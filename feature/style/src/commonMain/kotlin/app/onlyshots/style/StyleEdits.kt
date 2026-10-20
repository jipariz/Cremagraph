package app.onlyshots.style

import app.onlyshots.core.model.Series
import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.DarkSet
import app.onlyshots.core.model.style.Dash
import app.onlyshots.core.model.style.GlassSettings
import app.onlyshots.core.model.style.Glow
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.model.style.GridMode
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.model.style.Reading
import app.onlyshots.core.model.style.SeriesColors
import app.onlyshots.core.model.style.Shadow
import app.onlyshots.core.model.style.Stop
import app.onlyshots.core.model.style.TargetLook
import app.onlyshots.core.model.style.TileStyle
import app.onlyshots.overlay.GraphStyles

/*
 * Every change the style editor can make, one function per control. The screen calls these and nothing else, so what a
 * user can build is exactly what these reach; BuiltInsAreReplicableTest rebuilds every built-in style with them alone.
 */

// Panel ---------------------------------------------------------------------------------------

/** Another panel material: tiles only exist over blur (and the tile cells with them), and no panel has no gloss. */
fun GraphStyle.withMaterial(m: Material): GraphStyle {
    val keepTiles = m == Material.BLUR
    return copy(
        panel =
            panel.copy(
                material = m,
                tiles = if (keepTiles) panel.tiles else null,
                gradient = if (m == Material.NONE) null else panel.gradient,
                opacity = if (m == Material.SOLID) panel.opacity.coerceAtLeast(0.2f) else panel.opacity,
            ),
        cells =
            if (!keepTiles && cells.look == CellLook.TILES) cells.copy(look = CellLook.FILLED, fill = DefaultCell)
            else cells,
    )
}

/** The panel's colour (the tint, over blur and glass); its opacity is set on its own. */
fun GraphStyle.withPanelColor(argb: Int): GraphStyle = copy(panel = panel.copy(color = argb or OPAQUE))

/** How much of the panel's colour shows: the solid panel's opacity, the tint's strength over blur and glass. */
fun GraphStyle.withPanelOpacity(v: Float): GraphStyle = copy(panel = panel.copy(opacity = v))

fun GraphStyle.withPanelBlur(v: Float): GraphStyle = copy(panel = panel.copy(blur = v))

fun GraphStyle.withCorners(v: Float): GraphStyle = copy(panel = panel.copy(radius = v))

fun GraphStyle.withPadding(v: Float): GraphStyle = copy(panel = panel.copy(padding = v))

fun GraphStyle.withPanelShadow(shadow: Shadow?): GraphStyle = copy(panel = panel.copy(shadow = shadow))

/** A 1 px line just outside the panel; 0 for none. */
fun GraphStyle.withOuterBorder(argb: Int): GraphStyle = copy(panel = panel.copy(outerBorder = argb))

/** A 1 px line just inside the panel; 0 for none. */
fun GraphStyle.withInnerBorder(argb: Int): GraphStyle = copy(panel = panel.copy(innerBorder = argb))

fun GraphStyle.withGlow(glow: Glow?): GraphStyle = copy(panel = panel.copy(glow = glow))

fun GraphStyle.withGlass(glass: GlassSettings): GraphStyle = copy(panel = panel.copy(glass = glass))

/** A two-tone gloss over the panel, or none for its flat colour. */
fun GraphStyle.withPanelGloss(gloss: Gloss?): GraphStyle = copy(panel = panel.copy(gradient = gloss?.stops()))

/**
 * Acrylic tiles instead of one panel (Metro): the header and chart one tile, each reading its own. Over blur only. With
 * tiles off, cells that were tiles become filled cells.
 */
fun GraphStyle.withTiles(tiles: TileStyle?): GraphStyle =
    copy(
        panel = panel.copy(tiles = tiles.takeIf { panel.material == Material.BLUR }),
        cells =
            if (tiles == null && cells.look == CellLook.TILES) cells.copy(look = CellLook.FILLED, fill = DefaultCell)
            else cells,
    )

// Lines ---------------------------------------------------------------------------------------

fun GraphStyle.withSeriesColor(which: Series, argb: Int): GraphStyle = copy(series = series.with(which, argb or OPAQUE))

/** The width of pressure and flow ([main]) and of the other curves ([secondary]). */
fun GraphStyle.withLineWeights(main: Float, secondary: Float): GraphStyle =
    copy(lines = lines.copy(main = main, secondary = secondary))

fun GraphStyle.withStepped(stepped: Boolean): GraphStyle = copy(lines = lines.copy(stepped = stepped))

fun GraphStyle.withArea(v: Float): GraphStyle = copy(lines = lines.copy(area = v))

fun GraphStyle.withTargets(look: TargetLook): GraphStyle = copy(lines = lines.copy(targets = look))

fun GraphStyle.withTargetOpacity(v: Float): GraphStyle = copy(lines = lines.copy(targetOpacity = v))

/** The target lines' width; null for as wide as the secondary curves. */
fun GraphStyle.withTargetWidth(v: Float?): GraphStyle = copy(lines = lines.copy(targetWidth = v))

fun GraphStyle.withLineShadow(shadow: Shadow?): GraphStyle = copy(lines = lines.copy(shadow = shadow))

// Grid ----------------------------------------------------------------------------------------

/** The grid's lines; a grid switched on with no colour yet gets the default one. */
fun GraphStyle.withGrid(mode: GridMode): GraphStyle =
    copy(grid = grid.copy(mode = mode, color = if (grid.color == 0) DefaultGrid else grid.color))

fun GraphStyle.withGridColor(argb: Int): GraphStyle = copy(grid = grid.copy(color = argb))

fun GraphStyle.withGridDashed(dashed: Boolean): GraphStyle =
    copy(grid = grid.copy(dash = if (dashed) GridDash else null))

// Text ----------------------------------------------------------------------------------------

fun GraphStyle.withTitleColor(argb: Int): GraphStyle = copy(text = text.copy(title = argb))

fun GraphStyle.withLabelColor(argb: Int): GraphStyle = copy(text = text.copy(label = argb))

fun GraphStyle.withTargetTextColor(argb: Int): GraphStyle = copy(text = text.copy(target = argb))

/** A shadow (or, with no offset, a halo) under every text; null for none. */
fun GraphStyle.withTextEffect(effect: Shadow?): GraphStyle = copy(text = text.copy(effect = effect))

fun GraphStyle.withPhaseLineColor(argb: Int): GraphStyle = copy(phase = phase.copy(line = argb))

fun GraphStyle.withPhaseLabelColor(argb: Int): GraphStyle = copy(phase = phase.copy(label = argb))

// Readings ------------------------------------------------------------------------------------

/**
 * Another cell look. Each starts from something to see: filled cells from the default fill, glossy ones from a gloss
 * around the panel's colour, tiles from Metro's colours. Tiles need the panel's tiles.
 */
fun GraphStyle.withCellLook(look: CellLook): GraphStyle {
    if (look == CellLook.TILES && panel.tiles == null) return this
    return copy(
        cells =
            cells.copy(
                look = look,
                fill = if (cells.fill == 0) DefaultCell else cells.fill,
                gradient =
                    if (look == CellLook.GLOSSY && cells.gradient == null) Gloss.around(panel.color).stops()
                    else cells.gradient,
                tiles =
                    if (look == CellLook.TILES && cells.tiles.isEmpty()) GraphStyles.Metro.cells.tiles else cells.tiles,
            )
    )
}

fun GraphStyle.withCellFill(argb: Int): GraphStyle = copy(cells = cells.copy(fill = argb))

fun GraphStyle.withCellBorder(argb: Int): GraphStyle = copy(cells = cells.copy(border = argb))

fun GraphStyle.withCellRadius(v: Float): GraphStyle = copy(cells = cells.copy(radius = v))

/** The glossy cells' two-tone gradient. */
fun GraphStyle.withCellGloss(gloss: Gloss?): GraphStyle = copy(cells = cells.copy(gradient = gloss?.stops()))

/** A reading's own tile colour, for tile cells. */
fun GraphStyle.withTileColor(reading: Reading, argb: Int): GraphStyle =
    copy(cells = cells.copy(tiles = cells.tiles + (reading to (argb or OPAQUE))))

/** A reading's value colour. */
fun GraphStyle.withValueColor(reading: Reading, argb: Int): GraphStyle =
    copy(cells = cells.copy(values = cells.values + (reading to (argb or OPAQUE))))

/** Every value white. */
fun GraphStyle.withWhiteValues(): GraphStyle =
    copy(cells = cells.copy(values = Reading.entries.associateWith { WHITE }))

/** Every value in its series' colour, the time white. */
fun GraphStyle.withSeriesValues(): GraphStyle =
    copy(
        cells =
            cells.copy(
                values =
                    mapOf(
                        Reading.TIME to WHITE,
                        Reading.WEIGHT to series.weight,
                        Reading.PRESSURE to series.pressure,
                        Reading.FLOW to series.flow,
                        Reading.WEIGHT_FLOW to series.weightFlow,
                        Reading.TEMPERATURE to series.temperature,
                    )
            )
    )

/** 300, 400 or 600. */
fun GraphStyle.withValueWeight(weight: Int): GraphStyle = copy(cells = cells.copy(valueWeight = weight))

// Adapt to video ------------------------------------------------------------------------------

/** Adapting needs a dark set to switch to; Minimal's is the one it starts from. */
fun GraphStyle.withAdapt(on: Boolean): GraphStyle =
    copy(adapt = adapt.copy(enabled = on, dark = adapt.dark ?: GraphStyles.Minimal.adapt.dark))

fun GraphStyle.withScrim(on: Boolean): GraphStyle = copy(adapt = adapt.copy(scrim = on))

private fun GraphStyle.withDark(change: (DarkSet) -> DarkSet): GraphStyle =
    copy(adapt = adapt.copy(dark = change(adapt.dark ?: GraphStyles.Minimal.adapt.dark!!)))

fun GraphStyle.withDarkText(argb: Int): GraphStyle = withDark { it.copy(text = argb) }

fun GraphStyle.withDarkSeries(series: Series, argb: Int): GraphStyle = withDark {
    it.copy(series = it.series.with(series, argb or OPAQUE))
}

fun GraphStyle.withDarkEffect(effect: Shadow?): GraphStyle = withDark { it.copy(effect = effect) }

// Shared --------------------------------------------------------------------------------------

/**
 * A two-tone gloss: the top half from [topFrom] to [topTo], a hard step at [split], the bottom from [bottomFrom] to
 * [bottomTo]. As four gradient stops, two of them at the split.
 */
data class Gloss(val topFrom: Int, val topTo: Int, val split: Float, val bottomFrom: Int, val bottomTo: Int) {
    fun stops(): List<Stop> = listOf(Stop(0f, topFrom), Stop(split, topTo), Stop(split, bottomFrom), Stop(1f, bottomTo))

    companion object {
        /** The gloss these stops make, when they are one (four stops, a hard step in the middle); else null. */
        fun of(stops: List<Stop>?): Gloss? {
            if (stops == null || stops.size != 4 || stops[1].at != stops[2].at) return null
            return Gloss(stops[0].color, stops[1].color, stops[1].at, stops[2].color, stops[3].color)
        }

        /** A gloss to start from: lighter over the top, darker under the step, around [base]. */
        fun around(base: Int): Gloss =
            Gloss(
                topFrom = lighten(base, 0.35f).withAlphaOf(0xB3),
                topTo = base.withAlphaOf(0xAD),
                split = 0.5f,
                bottomFrom = darken(base, 0.35f).withAlphaOf(0xCC),
                bottomTo = darken(base, 0.45f).withAlphaOf(0xD6),
            )
    }
}

fun SeriesColors.with(series: Series, argb: Int): SeriesColors =
    when (series) {
        Series.PRESSURE -> copy(pressure = argb)
        Series.FLOW -> copy(flow = argb)
        Series.WEIGHT -> copy(weight = argb)
        Series.WEIGHT_FLOW -> copy(weightFlow = argb)
        Series.TEMPERATURE -> copy(temperature = argb)
        Series.TARGETS -> this
    }

fun SeriesColors.of(series: Series): Int =
    when (series) {
        Series.PRESSURE -> pressure
        Series.FLOW -> flow
        Series.WEIGHT -> weight
        Series.WEIGHT_FLOW -> weightFlow
        Series.TEMPERATURE -> temperature
        Series.TARGETS -> 0
    }

/** The series a style has a colour for, in the editor's order (targets are drawn in their series' colours). */
val StyleSeries = listOf(Series.PRESSURE, Series.FLOW, Series.WEIGHT, Series.WEIGHT_FLOW, Series.TEMPERATURE)

internal fun Int.alphaFraction(): Float = ((this ushr 24) and 0xFF) / 255f

private fun Int.withAlphaOf(a: Int): Int = (this and 0xFFFFFF) or (a shl 24)

private fun lighten(c: Int, t: Float): Int = mix(c, 0xFFFFFF, t)

private fun darken(c: Int, t: Float): Int = mix(c, 0x000000, t)

private fun mix(c: Int, to: Int, t: Float): Int {
    fun ch(shift: Int) = (((c shr shift) and 0xFF) * (1 - t) + ((to shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
    return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

internal const val OPAQUE = 0xFF000000.toInt()
internal const val WHITE = 0xFFFFFFFF.toInt()
internal const val DefaultGrid = 0x2EFFFFFF
internal const val DefaultCell = 0xFF1E2023.toInt()
internal val GridDash = Dash(3f, 3f)

/** What a newly switched-on panel shadow, line shadow, text effect or tiles start as. */
internal val PanelShadow = Shadow(0x80000000.toInt(), dy = 4f, blur = 16f)
internal val DefaultLineShadow = Shadow(0x99000000.toInt(), dy = 1f, blur = 2f)
internal val DefaultTextShadow = Shadow(0xD9000000.toInt(), dy = 1f, blur = 3f)
internal val DefaultHalo = Shadow(0x99FFFFFF.toInt(), dy = 0f, blur = 3f)
internal val DefaultGlow = Glow(0x593F99FF, 14f)
internal val DefaultTiles = TileStyle(gap = 4f, base = 0x66000000, edgeLine = 0x24FFFFFF)
