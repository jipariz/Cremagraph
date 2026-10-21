package app.onlyshots.core.model.style

import kotlinx.serialization.Serializable

/*
 * A graph style is a token set on the shared anatomy (header → chart → readings): the same fields for the four built-in
 * styles and for every style a user builds in the style editor. Data, not code, so a user style can be saved, set as a
 * source's default and copied into a video's sidecar.
 *
 * Colours are packed ARGB ints. Lengths are reference px at a 390-wide frame and scale with the frame.
 */

@Serializable data class Shadow(val color: Int, val dy: Float, val blur: Float)

@Serializable data class Dash(val on: Float, val off: Float)

/** A colour at a position 0..1 down a vertical gradient. Two stops at the same position make a hard step. */
@Serializable data class Stop(val at: Float, val color: Int)

@Serializable
enum class Material {
    /** A flat colour at an opacity. */
    SOLID,

    /** The video behind the panel blurred, under a tint; the same on every platform. */
    BLUR,

    /** Blur plus a light edge: Liquid Glass. */
    GLASS,

    /** No panel: lines and numbers straight on the video. */
    NONE,
}

/**
 * Liquid Glass, with the controls of the Liquid demo app (github.com/fletchmckee/liquid): the same eight sliders,
 * ranges and defaults. Haze draws it in the preview and the exporters run Haze's shader, both through the one mapping
 * in `overlay/glass/GlassMapping.kt`. The tint is the panel's colour at its opacity.
 */
@Serializable
data class GlassSettings(
    /** The blur under the glass, reference px, 0–30. */
    val frost: Float = 0f,
    /** How far in from the edge the lens bends the picture, as a share of the panel's shorter side, 0–0.5. */
    val refraction: Float = 0.25f,
    /** How strongly the lens bends it, 0–1. */
    val curve: Float = 0.25f,
    /** The width of the lit rim, as a share of the shorter side, 0–0.25. */
    val edge: Float = 0f,
    /** 0–2; 1 leaves the colours alone. */
    val saturation: Float = 1f,
    /** The corner radius in percent of the shorter side, 0–50 (50 is a capsule). */
    val corner: Float = 25f,
    /** Colour split at the lens, 0–1. */
    val dispersion: Float = 0f,
    /** 0–2; 1 leaves the picture alone. */
    val contrast: Float = 1f,
)

/** Soft light around the panel. */
@Serializable data class Glow(val color: Int, val radius: Float)

/**
 * The panel behind header, chart and readings.
 *
 * [color] and [opacity] are the panel colour for [Material.SOLID] and the tint for [Material.BLUR] and
 * [Material.GLASS]. [blur] is the radius of the background blur; [glass] holds the Liquid Glass settings.
 */
@Serializable
data class PanelStyle(
    val material: Material,
    val color: Int = 0,
    val opacity: Float = 1f,
    val blur: Float = 34f,
    /**
     * The glass material's settings, used when [material] is [Material.GLASS]: its frost and corner then stand in for
     * [blur] and [radius].
     */
    val glass: GlassSettings = GlassSettings(),
    val radius: Float = 10f,
    val padding: Float = 6f,
    val shadow: Shadow? = null,
    /** Replaces the flat colour with a vertical gradient (Aero's gloss). */
    val gradient: List<Stop>? = null,
    /** A 1 px line just outside the panel, and one just inside. */
    val outerBorder: Int = 0,
    val innerBorder: Int = 0,
    val glow: Glow? = null,
    /**
     * Acrylic tiles instead of one panel (Metro): the header and chart are one tile, each reading its own,
     * [TileStyle.gap] apart. Under them the blurred video and [TileStyle.base] across the whole panel, so the gaps are
     * the base too; then each tile's colour at [opacity], then the edge line.
     */
    val tiles: TileStyle? = null,
)

@Serializable data class TileStyle(val gap: Float = 4f, val base: Int = 0, val edgeLine: Int = 0)

@Serializable
enum class GridMode {
    NONE,
    ROWS,
    BOTH,
}

@Serializable data class GridStyle(val mode: GridMode, val color: Int = 0, val dash: Dash? = null)

@Serializable
data class SeriesColors(val pressure: Int, val flow: Int, val weight: Int, val weightFlow: Int, val temperature: Int)

@Serializable
enum class TargetLook {
    DASHED,
    DOTTED,
    FAINT,
}

@Serializable
data class LineStyle(
    val main: Float = 2.6f,
    val secondary: Float = 1.8f,
    val stepped: Boolean = false,
    /** Opacity of the fill under each curve; 0 draws none. */
    val area: Float = 0f,
    val targets: TargetLook = TargetLook.DASHED,
    /** Targets are drawn in their series' colour at this opacity. */
    val targetOpacity: Float = 1f,
    /** The target lines' width; null draws them as wide as the secondary curves. */
    val targetWidth: Float? = null,
    val shadow: Shadow? = null,
)

@Serializable
data class TextColors(
    val title: Int,
    val label: Int,
    val target: Int,
    /** A halo or a drop shadow under every text, for reading over bright video. */
    val effect: Shadow? = null,
)

/** The cells of the readings row, in display order. */
@Serializable
enum class Reading {
    TIME,
    WEIGHT,
    PRESSURE,
    FLOW,
    WEIGHT_FLOW,
    TEMPERATURE,
}

@Serializable
enum class CellLook {
    FILLED,
    OUTLINED,
    PLAIN,

    /** Aero's glass buttons: [CellsStyle.gradient] with a border. */
    GLOSSY,

    /** Metro's acrylic tiles, one colour per reading ([CellsStyle.tiles]), drawn like the panel's tiles. */
    TILES,
}

@Serializable
data class CellsStyle(
    val look: CellLook,
    val fill: Int = 0,
    val border: Int = 0,
    val borderWidth: Float = 1f,
    val radius: Float = 8f,
    /** The value colour of each reading. */
    val values: Map<Reading, Int>,
    val gradient: List<Stop>? = null,
    val tiles: Map<Reading, Int> = emptyMap(),
    /** 300, 400 or 600. */
    val valueWeight: Int = 400,
)

@Serializable data class PhaseStyle(val line: Int, val label: Int)

/** The darker set a style switches to over bright video, when [AdaptStyle.enabled]. */
@Serializable
data class DarkSet(
    val text: Int,
    val series: SeriesColors,
    val effect: Shadow? = null,
    val tint: Int? = null,
)

@Serializable
data class AdaptStyle(
    val enabled: Boolean = false,
    val dark: DarkSet? = null,
    /** A soft dark scrim behind the readings on frames where they would read below 4.5 : 1. */
    val scrim: Boolean = false,
)

@Serializable
data class GraphStyle(
    val id: String,
    val name: String,
    val panel: PanelStyle,
    val grid: GridStyle,
    val series: SeriesColors,
    val lines: LineStyle = LineStyle(),
    val text: TextColors,
    val cells: CellsStyle,
    val phase: PhaseStyle,
    val adapt: AdaptStyle = AdaptStyle(),
    val builtIn: Boolean = false,
) {
    /** Whether any part of this style blurs the video behind it, in the preview and in the export. */
    val blursVideo: Boolean
        get() =
            panel.material == Material.BLUR ||
                panel.material == Material.GLASS ||
                panel.tiles != null ||
                cells.look == CellLook.TILES
}
