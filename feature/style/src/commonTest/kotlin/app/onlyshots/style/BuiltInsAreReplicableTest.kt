package app.onlyshots.style

import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.model.style.GridMode
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.model.style.Reading
import app.onlyshots.overlay.GraphStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Every built-in style can be made by hand in the style editor: starting from a copy of another built-in, the editor's
 * own edits ([StyleEdits.kt]) — and only those its controls show in that state — rebuild it to what draws the same.
 */
class BuiltInsAreReplicableTest {
    /** What the editor would do, control by control, to turn [from] into [to]. */
    private fun rebuild(from: GraphStyle, to: GraphStyle): GraphStyle {
        val p = to.panel
        var s = from.withMaterial(p.material)
        // Panel
        if (p.material != Material.NONE) {
            s = s.withPanelColor(p.color).withPanelOpacity(p.opacity).withPadding(p.padding)
            s =
                s.withOuterBorder(p.outerBorder)
                    .withInnerBorder(p.innerBorder)
                    .withGlow(p.glow)
                    .withPanelShadow(p.shadow)
            s = s.withPanelGloss(p.gradient?.let { assertNotNull(Gloss.of(it), "${to.name}'s panel gloss") })
        }
        if (p.material == Material.SOLID || p.material == Material.BLUR) s = s.withCorners(p.radius)
        if (p.material == Material.BLUR) s = s.withPanelBlur(p.blur).withTiles(p.tiles)
        if (p.material == Material.GLASS) s = s.withGlass(p.glass)
        // Lines
        for (series in StyleSeries) s = s.withSeriesColor(series, to.series.of(series))
        s = s.withLineWeights(to.lines.main, to.lines.secondary).withStepped(to.lines.stepped).withArea(to.lines.area)
        s = s.withTargets(to.lines.targets).withTargetOpacity(to.lines.targetOpacity)
        s = s.withTargetWidth(to.lines.targetWidth).withLineShadow(to.lines.shadow)
        // Grid
        s = s.withGrid(to.grid.mode)
        if (to.grid.mode != GridMode.NONE) s = s.withGridColor(to.grid.color).withGridDashed(to.grid.dash != null)
        // Text
        s =
            s.withFont(to.text.font)
                .withLettering(to.text.lettering)
                .withTitleColor(to.text.title)
                .withLabelColor(to.text.label)
                .withTargetTextColor(to.text.target)
        s = s.withTextEffect(to.text.effect).withPhaseLineColor(to.phase.line).withPhaseLabelColor(to.phase.label)
        // Readings
        val c = to.cells
        s = s.withCellLook(c.look).withCellRadius(c.radius).withValueWeight(c.valueWeight)
        if (c.look == CellLook.FILLED || c.look == CellLook.OUTLINED)
            s = s.withCellFill(c.fill).withCellBorder(c.border)
        if (c.look == CellLook.GLOSSY) {
            s = s.withCellGloss(assertNotNull(Gloss.of(c.gradient), "${to.name}'s cell gloss")).withCellBorder(c.border)
        }
        if (c.look == CellLook.TILES) for ((r, color) in c.tiles) s = s.withTileColor(r, color)
        for (r in Reading.entries) c.values[r]?.let { s = s.withValueColor(r, it) }
        // Adapt to video
        s = s.withAdapt(to.adapt.enabled)
        if (to.adapt.enabled) {
            val dark = assertNotNull(to.adapt.dark)
            s = s.withDarkText(dark.text).withDarkEffect(dark.effect).withScrim(to.adapt.scrim)
            for (series in StyleSeries) s = s.withDarkSeries(series, dark.series.of(series))
        }
        return s
    }

    /**
     * The style as it draws: what a control the editor hides in this state holds does not show, and the name, id and
     * built-in flag are not looks.
     */
    private fun GraphStyle.visible(): GraphStyle {
        val m = panel.material
        return copy(
            id = "",
            name = "",
            builtIn = false,
            panel =
                panel.copy(
                    color = if (m == Material.NONE) 0 else panel.color,
                    opacity = if (m == Material.NONE) 0f else panel.opacity,
                    padding = if (m == Material.NONE) 0f else panel.padding,
                    radius = if (m == Material.SOLID || m == Material.BLUR) panel.radius else 0f,
                    blur = if (m == Material.BLUR) panel.blur else 0f,
                    glass = if (m == Material.GLASS) panel.glass else app.onlyshots.core.model.style.GlassSettings(),
                    shadow = if (m == Material.NONE) null else panel.shadow,
                    gradient = if (m == Material.NONE) null else panel.gradient,
                    outerBorder = if (m == Material.NONE) 0 else panel.outerBorder,
                    innerBorder = if (m == Material.NONE) 0 else panel.innerBorder,
                    glow = if (m == Material.NONE) null else panel.glow,
                ),
            grid = if (grid.mode == GridMode.NONE) grid.copy(color = 0, dash = null) else grid,
            cells =
                cells.copy(
                    fill = if (cells.look == CellLook.FILLED || cells.look == CellLook.OUTLINED) cells.fill else 0,
                    border = if (cells.look == CellLook.PLAIN || cells.look == CellLook.TILES) 0 else cells.border,
                    gradient = if (cells.look == CellLook.GLOSSY) cells.gradient else null,
                    tiles = if (cells.look == CellLook.TILES) cells.tiles else emptyMap(),
                ),
            adapt = if (adapt.enabled) adapt else adapt.copy(dark = null, scrim = false),
        )
    }

    private fun assertRebuilds(from: GraphStyle, to: GraphStyle) =
        assertEquals(to.visible(), rebuild(from, to).visible(), "${to.name} from a copy of ${from.name}")

    @Test
    fun everyBuiltInFromEveryOther() {
        for (to in GraphStyles.all) for (from in GraphStyles.all) if (from != to) assertRebuilds(from, to)
    }

    @Test
    fun minimalsLineShadowAndMetrosTilesAreTheEditorsToo() {
        val minimal = rebuild(GraphStyles.Gaggiuino, GraphStyles.Minimal)
        assertEquals(GraphStyles.Minimal.lines.shadow, minimal.lines.shadow)
        val metro = rebuild(GraphStyles.Gaggiuino, GraphStyles.Metro)
        assertEquals(GraphStyles.Metro.panel.tiles, metro.panel.tiles)
        assertEquals(GraphStyles.Metro.cells.tiles, metro.cells.tiles)
    }
}
