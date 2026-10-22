package app.onlyshots.overlay

import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.style.AdaptStyle
import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.CellsStyle
import app.onlyshots.core.model.style.DarkSet
import app.onlyshots.core.model.style.GlassSettings
import app.onlyshots.core.model.style.Glow
import app.onlyshots.core.model.style.GridStyle
import app.onlyshots.core.model.style.Lettering
import app.onlyshots.core.model.style.LineStyle
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.model.style.OverlayFont
import app.onlyshots.core.model.style.PanelStyle
import app.onlyshots.core.model.style.PhaseStyle
import app.onlyshots.core.model.style.Stop
import app.onlyshots.core.model.style.TextColors
import app.onlyshots.core.model.style.TileStyle

/**
 * The built-in graph styles: the app's own Instrument, the four of Figma's page 09, and Liquid Glass to show off the
 * glass material. Token values are Figma's "Style tokens" tables (page 09 · Graph styles), which are the source of
 * truth; users build anything else in the style editor.
 */
object GraphStyles {
    private fun byReading(time: Int, weight: Int, pressure: Int, flow: Int, weightFlow: Int, temperature: Int) =
        mapOf(
            Reading.TIME to time,
            Reading.WEIGHT to weight,
            Reading.PRESSURE to pressure,
            Reading.FLOW to flow,
            Reading.WEIGHT_FLOW to weightFlow,
            Reading.TEMPERATURE to temperature,
        )

    private fun allReadings(c: Argb) = Reading.entries.associateWith { c }

    /**
     * The app's own look ("Instrument"): an ink panel, a hairline grid of rows and columns, square outlined cells, the
     * signal pressure curve, and every text in Plex Mono, lettered as a readout (grey capitals, a rule under the title,
     * readings named by their units).
     */
    val Instrument =
        GraphStyle(
            id = "instrument",
            name = "Instrument",
            builtIn = true,
            panel = PanelStyle(Material.SOLID, color = rgb(0x0B0B0B), opacity = 0.88f, radius = 0f, padding = 6f),
            grid = GridStyle(GridMode.BOTH, rgb(0x2A2A28)),
            series =
                SeriesColors(
                    pressure = rgb(0xFF5A1F),
                    flow = rgb(0xE8E6E1),
                    weight = rgb(0x7FA7B8),
                    weightFlow = rgb(0x5CD08A),
                    temperature = rgb(0xFFB020),
                ),
            lines = LineStyle(main = 2f, secondary = 1.6f, targetOpacity = 0.5f),
            text =
                TextColors(
                    title = rgb(0xA3A19B),
                    label = rgb(0xA3A19B),
                    target = rgb(0xA3A19B),
                    font = OverlayFont.MONO,
                    lettering = Lettering.READOUT,
                ),
            cells =
                CellsStyle(
                    CellLook.OUTLINED,
                    border = rgb(0x3A3A38),
                    radius = 0f,
                    values =
                        byReading(
                            time = rgb(0xE8E6E1),
                            weight = rgb(0x7FA7B8),
                            pressure = rgb(0xFF5A1F),
                            flow = rgb(0xE8E6E1),
                            weightFlow = rgb(0x5CD08A),
                            temperature = rgb(0xFFB020),
                        ),
                ),
            phase = PhaseStyle(line = rgba(163, 161, 155, 0.5f), label = rgb(0xA3A19B)),
        )

    /** The Gaggiuino web interface: near-black panel, dashed grids, its blue / amber / rose curves. */
    val Gaggiuino =
        GraphStyle(
            id = "gaggiuino",
            name = "Gaggiuino",
            builtIn = true,
            panel = PanelStyle(Material.SOLID, color = rgb(0x121416), opacity = 0.92f, radius = 10f, padding = 6f),
            grid = GridStyle(GridMode.BOTH, rgba(255, 255, 255, 0.13f), Dash(3f, 3f)),
            series = SeriesColors(rgb(0x3A90DA), rgb(0xFFB53E), rgb(0xA75C58), rgb(0x1D7835), rgb(0xEF4040)),
            lines = LineStyle(targetOpacity = 1f),
            text = TextColors(title = rgb(0xE8E8E8), label = rgb(0xA9ABAE), target = rgb(0xA9ABAE)),
            cells =
                CellsStyle(
                    CellLook.FILLED,
                    fill = rgb(0x1E2023),
                    border = rgba(255, 255, 255, 0.12f),
                    radius = 8f,
                    values =
                        byReading(
                            time = rgb(0xFFFFFF),
                            weight = rgb(0xD98A90),
                            pressure = rgb(0x5AA6EA),
                            flow = rgb(0xFFB53E),
                            weightFlow = rgb(0x3FB862),
                            temperature = rgb(0xF26B6B),
                        ),
                ),
            phase = PhaseStyle(line = rgba(255, 255, 255, 0.25f), label = rgb(0xA9ABAE)),
        )

    /**
     * Windows Phone tiles: the header and chart are one cobalt tile, each reading its own colour tile, solid, on a
     * black base that shows as the 2 px lines between them.
     */
    val Metro =
        GraphStyle(
            id = "metro",
            name = "Metro",
            builtIn = true,
            panel =
                PanelStyle(
                    Material.BLUR,
                    color = rgb(0x0050EF),
                    opacity = 1f,
                    blur = 24f,
                    radius = 0f,
                    padding = 6f,
                    tiles = TileStyle(gap = 2f, base = rgb(0x000000), edgeLine = rgba(255, 255, 255, 0.14f)),
                ),
            grid = GridStyle(GridMode.ROWS, rgba(255, 255, 255, 0.18f)),
            // Each line a light tint of its reading's tile below, so they pair up, and light enough to read on cobalt.
            series =
                SeriesColors(
                    pressure = rgb(0xFF6EC7),
                    flow = rgb(0xF0B860),
                    weight = rgb(0x7EE07E),
                    weightFlow = rgb(0xC88CFF),
                    temperature = rgb(0xFF7F7F),
                ),
            lines = LineStyle(targetOpacity = 0.8f),
            text =
                TextColors(
                    title = rgb(0xFFFFFF),
                    label = rgba(255, 255, 255, 0.85f),
                    target = rgba(255, 255, 255, 0.85f),
                ),
            cells =
                CellsStyle(
                    CellLook.TILES,
                    radius = 0f,
                    values = allReadings(rgb(0xFFFFFF)),
                    tiles =
                        byReading(
                            time = rgb(0x4E5D6C),
                            weight = rgb(0x007A00),
                            pressure = rgb(0xC4006A),
                            flow = rgb(0x74501F),
                            weightFlow = rgb(0x9A00E6),
                            temperature = rgb(0xA20025),
                        ),
                    valueWeight = 300,
                ),
            phase = PhaseStyle(line = rgba(255, 255, 255, 0.4f), label = rgb(0xFFFFFF)),
        )

    /** Windows Vista glass: a glossy blue glass panel over the video, a light inner edge and a soft blue glow. */
    val Aero =
        GraphStyle(
            id = "aero",
            name = "Aero",
            builtIn = true,
            panel =
                PanelStyle(
                    Material.GLASS,
                    color = rgb(0x5D93C9),
                    opacity = 1f,
                    // Clear glass under the gloss: little frost, a lens that bends the video at the panel's edge, a lit
                    // rim.
                    glass = GlassSettings(frost = 3f, refraction = 0.3f, curve = 0.25f, edge = 0.03f, corner = 4f),
                    radius = 10f,
                    padding = 6f,
                    gradient =
                        listOf(
                            // See-through, so the glass under it shows; the step still reads as Vista's gloss.
                            Stop(0f, rgba(0x8E, 0xC0, 0xEC, 0.45f)),
                            Stop(0.5f, rgba(0x5D, 0x93, 0xC9, 0.30f)),
                            Stop(0.5f, rgba(0x2C, 0x5E, 0x96, 0.42f)),
                            Stop(1f, rgba(0x23, 0x4F, 0x82, 0.55f)),
                        ),
                    outerBorder = rgba(0x0D, 0x1F, 0x38, 0.85f),
                    innerBorder = rgba(255, 255, 255, 0.70f),
                    glow = Glow(rgba(0x3F, 0x99, 0xFF, 0.35f), 14f),
                ),
            grid = GridStyle(GridMode.BOTH, rgba(255, 255, 255, 0.18f)),
            series = SeriesColors(rgb(0x4FD2FF), rgb(0x9BE15D), rgb(0xFFFFFF), rgb(0xFFD86B), rgb(0xFF8A6B)),
            lines = LineStyle(targetOpacity = 0.7f),
            text =
                TextColors(
                    title = rgb(0xFFFFFF),
                    label = rgba(0xEA, 0xF6, 0xFF, 0.92f),
                    target = rgba(0xEA, 0xF6, 0xFF, 0.92f),
                    effect = Shadow(rgba(0x00, 0x1A, 0x40, 0.8f), dy = 1f, blur = 3f),
                ),
            cells =
                CellsStyle(
                    CellLook.GLOSSY,
                    border = rgba(255, 255, 255, 0.55f),
                    radius = 6f,
                    values = allReadings(rgb(0xFFFFFF)),
                    gradient =
                        listOf(
                            Stop(0f, rgba(255, 255, 255, 0.38f)),
                            Stop(0.52f, rgba(255, 255, 255, 0.12f)),
                            Stop(0.52f, rgba(0x00, 0x1A, 0x40, 0.18f)),
                            Stop(1f, rgba(0x00, 0x26, 0x59, 0.28f)),
                        ),
                ),
            phase = PhaseStyle(line = rgba(255, 255, 255, 0.4f), label = rgb(0xEAF6FF)),
        )

    /**
     * iOS 26's Liquid Glass, to show what the glass material can do: a clear, barely tinted panel that refracts the
     * video at its rounded edge, a bright rim lit from the top left, a touch of dispersion, and iOS system colours. It
     * adapts to the video like the system material does.
     */
    val LiquidGlass =
        GraphStyle(
            id = "liquidglass",
            name = "Liquid Glass",
            builtIn = true,
            panel =
                PanelStyle(
                    Material.GLASS,
                    color = rgb(0xFFFFFF),
                    opacity = 0.08f,
                    padding = 9f,
                    glass =
                        // Clear, not frosted: a deep lens at the edge, a bright rim, colour split where it bends.
                        GlassSettings(
                            frost = 1f,
                            refraction = 0.4f,
                            curve = 0.4f,
                            edge = 0.04f,
                            saturation = 1.3f,
                            corner = 10f,
                            dispersion = 0.25f,
                            contrast = 1f,
                        ),
                ),
            grid = GridStyle(GridMode.ROWS, rgba(255, 255, 255, 0.14f)),
            series = SeriesColors(rgb(0x0A84FF), rgb(0xFF9F0A), rgb(0xFF375F), rgb(0x63E6E2), rgb(0xFF453A)),
            lines = LineStyle(main = 2.8f, targetOpacity = 0.6f),
            text =
                TextColors(
                    title = rgb(0xFFFFFF),
                    label = rgba(255, 255, 255, 0.78f),
                    target = rgba(255, 255, 255, 0.7f),
                    effect = Shadow(rgba(0, 0, 0, 0.45f), dy = 1f, blur = 4f),
                ),
            cells =
                CellsStyle(
                    CellLook.FILLED,
                    fill = rgba(0, 0, 0, 0.18f),
                    border = rgba(255, 255, 255, 0.22f),
                    radius = 14f,
                    values =
                        byReading(
                            time = rgb(0xFFFFFF),
                            weight = rgb(0xFF8FA3),
                            pressure = rgb(0x8CC8FF),
                            flow = rgb(0xFFC266),
                            weightFlow = rgb(0x63E6E2),
                            temperature = rgb(0xFF9A92),
                        ),
                    valueWeight = 600,
                ),
            phase = PhaseStyle(line = rgba(255, 255, 255, 0.3f), label = rgba(255, 255, 255, 0.78f)),
            adapt =
                AdaptStyle(
                    enabled = true,
                    dark =
                        DarkSet(
                            text = rgb(0x1C1C1E),
                            series =
                                SeriesColors(rgb(0x0040DD), rgb(0xC93400), rgb(0xD30F45), rgb(0x0C817B), rgb(0xD70015)),
                            effect = Shadow(rgba(255, 255, 255, 0.5f), dy = 0f, blur = 3f),
                        ),
                ),
        )

    /** No panel: lines and numbers only, with a soft shadow; darker over bright frames. */
    val Minimal =
        GraphStyle(
            id = "minimal",
            name = "Minimal",
            builtIn = true,
            panel = PanelStyle(Material.NONE, radius = 0f, padding = 0f),
            grid = GridStyle(GridMode.NONE),
            series = SeriesColors(rgb(0x9CC8FF), rgb(0xFFD983), rgb(0xFFFFFF), rgb(0x9FE8DE), rgb(0xFFAA88)),
            lines = LineStyle(targetOpacity = 0.7f, shadow = Shadow(rgba(0, 0, 0, 0.6f), dy = 1f, blur = 2f)),
            text =
                TextColors(
                    title = rgb(0xFFFFFF),
                    label = rgba(255, 255, 255, 0.85f),
                    target = rgba(255, 255, 255, 0.85f),
                    effect = Shadow(rgba(0, 0, 0, 0.85f), dy = 1f, blur = 3f),
                ),
            cells = CellsStyle(CellLook.PLAIN, radius = 0f, values = allReadings(rgb(0xFFFFFF))),
            phase = PhaseStyle(line = rgba(255, 255, 255, 0.5f), label = rgb(0xFFFFFF)),
            adapt =
                AdaptStyle(
                    enabled = true,
                    dark =
                        DarkSet(
                            text = rgb(0x1A1412),
                            series =
                                SeriesColors(
                                    rgb(0x1F5FA8),
                                    rgb(0x8A5E00),
                                    rgb(0x2A2421),
                                    rgb(0x0F7A6E),
                                    rgb(0xB4401A),
                                ),
                            effect = Shadow(rgba(255, 255, 255, 0.6f), dy = 0f, blur = 3f),
                        ),
                    scrim = true,
                ),
        )

    /** In picker order. */
    val all: List<GraphStyle> = listOf(Instrument, Gaggiuino, Metro, Aero, LiquidGlass, Minimal)

    private val byId = all.associateBy { it.id }

    /**
     * Ids of the styles that came before these four, so old videos and settings still open: the Gaggiuino looks become
     * Gaggiuino, the rest Metro.
     */
    private val legacy =
        mapOf(
            "gweb" to "gaggiuino",
            "gscreen" to "gaggiuino",
            "tiles" to "metro",
            "gmate" to "metro",
            "decentapp" to "metro",
            "decent" to "metro",
            "decentdark" to "metro",
            "wendougee" to "metro",
        )

    fun byId(id: String): GraphStyle? = byId[id] ?: legacy[id]?.let { byId[it] }

    /** The built-in default, for every source: Instrument, until the user picks another or makes one their default. */
    val Default: GraphStyle
        get() = Instrument

    /**
     * The style a video or the record screen draws with.
     * - A user style copied into [overlay] wins: the video keeps the look it was made with.
     * - [OverlaySettings.STYLE_AUTO] is the source's default: the user's own default for it from [defaults], else the
     *   built-in one.
     * - An id is looked up in [custom] (the user's styles), then among the built-ins and their old ids.
     * - Anything unknown falls back to the source's default.
     */
    fun resolve(
        overlay: OverlaySettings,
        source: SourceKind?,
        custom: List<GraphStyle> = emptyList(),
        defaults: Map<SourceKind, String> = emptyMap(),
    ): GraphStyle {
        overlay.style
            ?.takeIf { it.id == overlay.styleId }
            ?.let {
                return it
            }
        return resolve(overlay.styleId, source, custom, defaults)
    }

    fun resolve(
        styleId: String,
        source: SourceKind?,
        custom: List<GraphStyle> = emptyList(),
        defaults: Map<SourceKind, String> = emptyMap(),
    ): GraphStyle {
        fun find(id: String) = custom.firstOrNull { it.id == id } ?: byId(id)
        fun sourceDefault() = source?.let { defaults[it] }?.let(::find) ?: Default
        return if (styleId == OverlaySettings.STYLE_AUTO) sourceDefault() else find(styleId) ?: sourceDefault()
    }
}

/** The colour a small swatch of this style shows: its panel, the middle of its gradient, or nothing without a panel. */
val GraphStyle.surface: Argb
    get() =
        when {
            panel.material == Material.NONE -> TRANSPARENT
            panel.gradient != null -> panel.gradient!![panel.gradient!!.size / 2].color
            else -> panel.color.timesAlpha(maxOf(panel.opacity, 0.6f))
        }
