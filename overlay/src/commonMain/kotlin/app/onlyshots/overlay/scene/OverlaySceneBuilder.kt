package app.onlyshots.overlay.scene

import app.onlyshots.core.model.Layout
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Series
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.oneDecimal
import app.onlyshots.core.model.sampleAt
import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.model.style.TargetLook
import app.onlyshots.overlay.Argb
import app.onlyshots.overlay.Dash
import app.onlyshots.overlay.GraphStyle
import app.onlyshots.overlay.GridMode
import app.onlyshots.overlay.OverlayLabels
import app.onlyshots.overlay.Reading
import app.onlyshots.overlay.Shadow
import app.onlyshots.overlay.TRANSPARENT
import app.onlyshots.overlay.VALUE_CONTRAST
import app.onlyshots.overlay.alpha
import app.onlyshots.overlay.contrastOf
import app.onlyshots.overlay.rgba
import app.onlyshots.overlay.timesAlpha

/** Overlay anatomy in reference px at a 390-wide frame. */
object OverlayMetrics {
    const val HEADER = 16f
    const val GAP = 3f
    const val READINGS = 48f
    const val READINGS_MIN = 28f
    const val CHART_MIN = 16f
    const val TITLE_SIZE = 11f

    /** Height the header grows by, and the size it draws at, when a recipe line is set. */
    const val RECIPE_LINE = 11f
    const val RECIPE_SIZE = 9f
    const val LEGEND_SIZE = 9f
    const val LEGEND_SWATCH = 10f
    const val LABEL_SIZE = 9f
    const val VALUE_SIZE = 20f
    const val VALUE_SIZE_NARROW = 16f
    const val VALUE_SIZE_READINGS_ONLY = 24f
    const val TARGET_SIZE = 8f
    const val PHASE_SIZE = 8f
    const val CELL_PAD = 5f
    const val MAIN_LINE = 2.6f
    const val SECONDARY_LINE = 1.8f
    const val GRID_LINE = 1f
    val TARGET_DASH = Dash(5f, 4f)

    /**
     * Starting scales, grown with the shot: once a curve gets near the top it stretches to [AXIS_HEADROOM] over the
     * highest value so far. Temperature zooms to the shot instead.
     */
    const val PRESSURE_FLOW_MAX = 12f
    const val WEIGHT_MAX = 60f

    /**
     * The weight axis of a shot without a target weight (a scale's) fits the weight instead of starting at
     * [WEIGHT_MAX]: never below this, so the first drops are not blown up across the whole chart.
     */
    const val WEIGHT_FLOOR = 10f
    const val AXIS_HEADROOM = 1.1f
    const val TEMP_PAD = 2.5f

    const val MIN_WINDOW_US = 30_000_000L
    const val WINDOW_HEADROOM_US = 2_000_000L
    const val STEP_INTERVAL_US = 600_000L
    const val STEP_QUANTUM = 0.25f
}

private val CurveSeries = listOf(Series.WEIGHT, Series.WEIGHT_FLOW, Series.TEMPERATURE, Series.FLOW, Series.PRESSURE)
private val LegendSeries = listOf(Series.PRESSURE, Series.FLOW, Series.WEIGHT, Series.WEIGHT_FLOW, Series.TEMPERATURE)

internal fun Sample.value(s: Series): Float? =
    when (s) {
        Series.PRESSURE -> pressure
        Series.FLOW -> flow
        Series.WEIGHT -> weight
        Series.WEIGHT_FLOW -> weightFlow
        Series.TEMPERATURE -> temperature
        Series.TARGETS -> null
    }

/**
 * Length of the time axis at shot time [tUs]: 30 s, then growing smoothly with the shot so the curve keeps a little
 * headroom before the right edge; after the shot, the whole shot. It never shrinks as [tUs] grows.
 */
fun chartWindowUs(shot: Shot, tUs: Long): Long {
    val shown = tUs.coerceIn(0L, shot.durationUs.coerceAtLeast(0L))
    return maxOf(OverlayMetrics.MIN_WINDOW_US, shown + OverlayMetrics.WINDOW_HEADROOM_US)
}

/**
 * Selected curve series that the shot actually has data for, in legend order. Before its first sample (the record
 * screen, waiting for the shot) every selected one, so the readings wait at "–" rather than appearing as data arrives.
 */
fun visibleSeries(shot: Shot, selected: Set<Series>): List<Series> = LegendSeries.filter { s ->
    s in selected && (shot.samples.isEmpty() || shot.samples.any { it.value(s) != null })
}

/** Temperature axis: min and max of the shot (targets included) ± 2.5 °C. */
fun temperatureRange(shot: Shot): ClosedFloatingPointRange<Float> {
    var min = Float.MAX_VALUE
    var max = -Float.MAX_VALUE
    for (s in shot.samples) {
        s.temperature?.let {
            if (it < min) min = it
            if (it > max) max = it
        }
        s.targetTemperature?.let {
            if (it < min) min = it
            if (it > max) max = it
        }
    }
    if (min > max) {
        min = 93f
        max = 93f
    }
    return (min - OverlayMetrics.TEMP_PAD)..(max + OverlayMetrics.TEMP_PAD)
}

/**
 * Builds the overlay for video time [videoTimeUs] into a frame of [frameWidth] × [frameHeight] px.
 *
 * Pure and deterministic: the same input always gives the same primitives. Graph time 0 sits at [shotStartUs] on the
 * video timeline; curves are drawn up to the current time, targets for the whole shot.
 */
fun buildOverlayScene(
    shot: Shot,
    videoTimeUs: Long,
    shotStartUs: Long,
    settings: OverlaySettings,
    style: GraphStyle,
    title: String,
    frameWidth: Float,
    frameHeight: Float,
    metrics: TextMetrics,
    /** The words it draws (series, units, phases), resolved by the caller. */
    labels: OverlayLabels,
    /** Second header line: dose, yield, ratio, grinder. Omitted when null or blank. */
    recipe: String? = null,
    /**
     * Relative luminance (0..1) of the video behind the overlay, already smoothed by the caller. A style that adapts to
     * the video switches to its dark set over bright frames; null when unknown, which keeps the light set.
     */
    backdropLuma: Float? = null,
): OverlayScene =
    SceneBuilder(
            shot,
            videoTimeUs - shotStartUs,
            settings,
            style,
            title,
            frameWidth,
            frameHeight,
            metrics,
            labels,
            recipe,
            backdropLuma,
        )
        .build()

private class SceneBuilder(
    val shot: Shot,
    val tUs: Long,
    val settings: OverlaySettings,
    val style: GraphStyle,
    val title: String,
    frameWidth: Float,
    frameHeight: Float,
    val metrics: TextMetrics,
    val labels: OverlayLabels,
    val recipe: String? = null,
    val backdropLuma: Float? = null,
) {
    /** One reference px: the frame's short side is 390 of them in every format (its width at 9:16). */
    val u = minOf(frameWidth, frameHeight) / NormRect.REF_W
    val box =
        Box(
            settings.rect.x * frameWidth,
            settings.rect.y * frameHeight,
            settings.rect.right * frameWidth,
            settings.rect.bottom * frameHeight,
        )
    val out = ArrayList<Prim>(64)
    val series = visibleSeries(shot, settings.series)
    val showTargets = Series.TARGETS in settings.series

    /** The dark set, when the style adapts to the video and the video behind is bright. */
    val dark = style.adapt.dark?.takeIf { style.adapt.enabled && (backdropLuma ?: 0f) > ADAPT_BRIGHT }
    val palette = dark?.series ?: style.series
    val titleColor = dark?.text ?: style.text.title
    val labelColor = dark?.text ?: style.text.label
    val targetTextColor = dark?.text ?: style.text.target
    val textShadow = (if (dark != null) dark.effect else style.text.effect)?.scaled()
    val panel = style.panel
    val tiles = panel.tiles

    fun valueColor(r: Reading): Argb = dark?.text ?: style.cells.values[r] ?: titleColor

    val tempRange = temperatureRange(shot)
    val windowUs = chartWindowUs(shot, tUs)

    /**
     * The value axes at this moment: today's scales until a curve nears the top, then stretched over the highest value
     * shown so far, so nothing flatlines against the frame. Only ever growing, like the time axis.
     */
    val weightMax: Float
    val pressureFlowMax: Float

    init {
        var weight = 0f
        var pressureFlow = 0f
        var targeted = false
        for (sample in shot.samples) {
            // The target weight is a line across the whole chart: it counts from the start.
            sample.targetWeight?.let {
                weight = maxOf(weight, it)
                targeted = true
            }
            if (sample.tUs > windowUs) continue
            if (sample.tUs <= tUs) {
                sample.weight?.let { weight = maxOf(weight, it) }
                sample.pressure?.let { pressureFlow = maxOf(pressureFlow, it) }
                sample.flow?.let { pressureFlow = maxOf(pressureFlow, it) }
                // Weight flow is drawn on this axis too: a scale's, with no pressure or flow, sizes it alone.
                sample.weightFlow?.let { pressureFlow = maxOf(pressureFlow, it) }
            }
            // Targets are drawn ahead of the curve, up to the window's end.
            sample.targetPressure?.let { pressureFlow = maxOf(pressureFlow, it) }
            sample.targetFlow?.let { pressureFlow = maxOf(pressureFlow, it) }
        }
        // The curves' heads between two samples (see curveRuns) count too: the axis grows with them frame by frame, so
        // a head near the top stays there while the curve moves on, rather than rising past its place until the next
        // sample rescales everything at once.
        val last = shot.samples.lastOrNull()
        if (last != null && tUs in 0 until last.tUs && tUs <= windowUs) {
            shot.sampleAt(tUs)?.let { head ->
                head.weight?.let { weight = maxOf(weight, it) }
                head.pressure?.let { pressureFlow = maxOf(pressureFlow, it) }
                head.flow?.let { pressureFlow = maxOf(pressureFlow, it) }
                head.weightFlow?.let { pressureFlow = maxOf(pressureFlow, it) }
            }
        }
        // With a target the scale is the machine's (the target and 60 g); without one the axis follows the weight, so a
        // scale's shot fills the chart as it pours instead of creeping up the bottom of a 60 g axis.
        val base = if (targeted) OverlayMetrics.WEIGHT_MAX else OverlayMetrics.WEIGHT_FLOOR
        weightMax = maxOf(base, weight * OverlayMetrics.AXIS_HEADROOM)
        pressureFlowMax = maxOf(OverlayMetrics.PRESSURE_FLOW_MAX, pressureFlow * OverlayMetrics.AXIS_HEADROOM)
    }

    fun Shadow.scaled() = Shadow(color, dy * u, blur * u)

    /**
     * The transparency setting fades the graph's background only — the panel, its shadow, the chart area and the
     * reading tiles — never the curves, the text or the readings, which stay readable over any video.
     */
    fun Argb.bg(): Argb = timesAlpha(settings.opacity)

    /** The panel's corner radius as drawn: glass takes it from its own corner setting, a share of the shorter side. */
    val panelRadius: Float =
        if (panel.material == Material.GLASS && tiles == null)
            panel.glass.corner.coerceIn(0f, 50f) / 100f * minOf(box.width, box.height)
        else panel.radius * u

    fun build(): OverlayScene {
        // Round corners eat into the padding: grow it so the content's corners stay inside the rounded shape.
        val pad = maxOf(panel.padding * u, cornerInset(panelRadius, box))
        val gap = OverlayMetrics.GAP * u
        val tileGap = (tiles?.gap ?: 0f) * u
        // Without tiles the panel is one card and everything sits inside its padding. With tiles (Metro) the header and
        // chart share one tile, the readings are tiles of their own, and the padding is inside each tile.
        val area = if (tiles != null) box else box.inset(pad)
        val headerH =
            if (settings.layout == Layout.READINGS_ONLY) 0f
            else (OverlayMetrics.HEADER + if (hasRecipe) OverlayMetrics.RECIPE_LINE else 0f) * u
        var header: Box? = null
        var chart: Box? = null
        var readings: Box? = null
        var topTile: Box? = null
        when (settings.layout) {
            Layout.READINGS_ONLY -> readings = area
            Layout.GRAPH_ONLY -> {
                val top = if (tiles != null) area.inset(pad) else area
                header = Box(top.left, top.top, top.right, top.top + headerH)
                chart = Box(top.left, header.bottom + gap, top.right, top.bottom)
                topTile = if (tiles != null) area else null
            }
            Layout.GRAPH_AND_READINGS -> {
                val restH = area.height - headerH - gap - (if (tiles != null) 2 * pad else 0f)
                val readingsH =
                    (restH * 0.4f)
                        .coerceIn(OverlayMetrics.READINGS_MIN * u, OverlayMetrics.READINGS * u)
                        .coerceAtMost(restH.coerceAtLeast(0f))
                val split = if (tiles != null) tileGap else gap
                val chartH = restH - split - readingsH
                val topBottom =
                    if (chartH < OverlayMetrics.CHART_MIN * u) area.top + headerH + 2 * pad
                    else area.bottom - readingsH - split
                val top = Box(area.left, area.top, area.right, topBottom)
                val inTop = if (tiles != null) top.inset(pad) else top
                header = Box(inTop.left, inTop.top, inTop.right, inTop.top + headerH)
                if (chartH >= OverlayMetrics.CHART_MIN * u) {
                    chart = Box(inTop.left, header.bottom + gap, inTop.right, inTop.bottom)
                    readings = Box(area.left, area.bottom - readingsH, area.right, area.bottom)
                } else {
                    readings =
                        Box(
                            area.left,
                            (if (tiles != null) top.bottom + tileGap else header.bottom + gap),
                            area.right,
                            area.bottom,
                        )
                }
                topTile = if (tiles != null) top else null
            }
        }
        drawScrim()
        if (tiles != null) {
            drawTileBacking()
            topTile?.let { drawTile(it, panel.color) }
        } else drawPanel()
        header?.let { drawHeader(it) }
        chart?.let { drawChart(it) }
        readings?.let { drawReadings(it) }
        return OverlayScene(box, out)
    }

    // Panel ---------------------------------------------------------------------------------

    /** Soft layers widest first: a shadow or a glow, deterministic on every platform (no blur filter). */
    fun softLayers(around: Box, radius: Float, color: Argb, spread: Float, dy: Float) {
        val layers = 6
        val layerColor = color.timesAlpha(1f / layers).bg()
        for (i in layers downTo 1) {
            val grow = spread * i / layers
            val b = Box(around.left - grow, around.top - grow + dy, around.right + grow, around.bottom + grow + dy)
            out += RectPrim(b, layerColor, radius + grow)
        }
    }

    fun drawPanel() {
        val radius = panelRadius
        panel.glow?.let { softLayers(box, radius, it.color, it.radius * u, 0f) }
        panel.shadow?.let { softLayers(box, radius, it.color, it.blur * u / 2f, it.dy * u) }
        val tint = dark?.tint ?: panel.color
        when (panel.material) {
            Material.NONE -> {}
            Material.SOLID -> fill(box, radius, tint.timesAlpha(panel.opacity))
            Material.BLUR -> {
                out += BackdropPrim(box, radius, panel.blur * u)
                fill(box, radius, tint.timesAlpha(panel.opacity))
            }
            Material.GLASS -> {
                // The glass draws its own tint, rim and light; a gloss gradient (Aero) still goes on top of it.
                val glassTint = if (panel.gradient != null) TRANSPARENT else tint.timesAlpha(panel.opacity).bg()
                out += BackdropPrim(box, radius, panel.glass.frost * u, panel.glass, glassTint)
                panel.gradient?.let { fill(box, radius, tint) }
            }
        }
        if (panel.outerBorder.alpha > 0f) {
            val w = 1f * u
            out += RectPrim(box.inset(-w / 2f), panel.outerBorder, radius + w / 2f, stroke = w)
        }
        if (panel.innerBorder.alpha > 0f) {
            val w = 1f * u
            out += RectPrim(box.inset(w * 1.5f), panel.innerBorder, (radius - w * 1.5f).coerceAtLeast(0f), stroke = w)
        }
    }

    /** The panel's colour, or its gradient when it has one; both faded by the background opacity. */
    fun fill(b: Box, radius: Float, color: Argb) {
        val g = panel.gradient
        if (g != null)
            out += RectPrim(b, g.first().color, radius, gradient = g.map { GradientStop(it.at, it.color.bg()) })
        else if (color.alpha > 0f) out += RectPrim(b, color.bg(), radius)
    }

    /** An acrylic tile: the blurred video under a dark base, the tile's colour, and a hairline edge. */
    /**
     * Under the tiles, with a [TileStyle.base]: the blurred video and the base across the whole panel, gaps included,
     * so the gaps between the tiles are the base too (Metro's black lines). Without one, each tile blurs its own patch
     * and the gaps show the video.
     */
    fun drawTileBacking() {
        val t = tiles ?: return
        if (t.base.alpha <= 0f) return
        val radius = panel.radius * u
        out += BackdropPrim(box, radius, panel.blur * u)
        out += RectPrim(box, t.base.bg(), radius)
    }

    fun drawTile(b: Box, color: Argb) {
        val t = tiles ?: return
        val radius = panel.radius * u
        if (t.base.alpha <= 0f) out += BackdropPrim(b, radius, panel.blur * u)
        out += RectPrim(b, color.timesAlpha(panel.opacity).bg(), radius)
        if (t.edgeLine.alpha > 0f) out += RectPrim(b.inset(0.5f * u), t.edgeLine, radius, stroke = 1f * u)
    }

    /**
     * Minimal over a busy frame: where the light text would read below 4.5 : 1 and the dark set does not take over, a
     * feathered dark scrim behind the overlay keeps it readable.
     */
    fun drawScrim() {
        val luma = backdropLuma ?: return
        if (!style.adapt.scrim || dark != null) return
        if (contrastOf(titleColor, luma) >= VALUE_CONTRAST) return
        softLayers(box, 12f * u, rgba(0, 0, 0, 0.35f), 24f * u, 0f)
        out += RectPrim(box, rgba(0, 0, 0, 0.35f), 12f * u)
    }

    // Header --------------------------------------------------------------------------------

    val hasRecipe: Boolean
        get() = !recipe.isNullOrBlank()

    fun drawHeader(box: Box) {
        // The title row keeps its own height; the recipe, when there is one, sits under it.
        val header = if (hasRecipe) Box(box.left, box.top, box.right, box.top + OverlayMetrics.HEADER * u) else box
        val legendSize = OverlayMetrics.LEGEND_SIZE * u
        val swatch = OverlayMetrics.LEGEND_SWATCH * u
        val entryGap = 7f * u
        val entries = buildList {
            if (!settings.legend) return@buildList
            series.forEach { add(LegendEntry(labels.series(it), seriesColor(it), dashed = false)) }
            if (showTargets && hasAnyTarget())
                add(LegendEntry(labels.series(Series.TARGETS), targetTextColor, dashed = true))
        }
            .toMutableList()
        fun entryWidth(e: LegendEntry) = swatch + 3f * u + metrics.width(e.label, legendSize, 400)
        fun legendWidth() =
            entries.sumOf { entryWidth(it).toDouble() }.toFloat() + entryGap * (entries.size - 1).coerceAtLeast(0)
        val maxLegend = header.width * 0.62f
        while (entries.isNotEmpty() && legendWidth() > maxLegend) {
            // Keep the single target entry as long as any curve entry is left to drop.
            val dropAt = if (entries.size > 1 && entries.last().dashed) entries.size - 2 else entries.size - 1
            entries.removeAt(dropAt)
        }
        val legendW = legendWidth().coerceAtLeast(0f)

        val titleSize = OverlayMetrics.TITLE_SIZE * u
        val titleMax = header.width - legendW - if (entries.isEmpty()) 0f else 8f * u
        val titleText = ellipsize(title, titleMax, titleSize, 600)
        if (titleText.isNotEmpty()) {
            out +=
                TextPrim(
                    titleText,
                    header.left,
                    centeredBaseline(header, titleSize),
                    titleSize,
                    600,
                    titleColor,
                    shadow = textShadow,
                )
        }

        var x = header.right - legendW
        val baseline = centeredBaseline(header, legendSize)
        val lineY = (header.top + header.bottom) / 2f
        for (e in entries) {
            out +=
                PolylinePrim(
                    floatArrayOf(x, lineY, x + swatch, lineY),
                    e.color,
                    OverlayMetrics.SECONDARY_LINE * u,
                    if (e.dashed) Dash(3f * u, 2f * u) else null,
                )
            out +=
                TextPrim(
                    e.label,
                    x + swatch + 3f * u,
                    baseline,
                    legendSize,
                    400,
                    labelColor,
                    shadow = textShadow,
                )
            x += entryWidth(e) + entryGap
        }

        if (hasRecipe) {
            val size = OverlayMetrics.RECIPE_SIZE * u
            val line = Box(box.left, header.bottom, box.right, box.bottom)
            out +=
                TextPrim(
                    ellipsize(recipe!!, line.width, size, 400),
                    line.left,
                    centeredBaseline(line, size),
                    size,
                    400,
                    labelColor,
                    shadow = textShadow,
                )
        }
    }

    data class LegendEntry(val label: String, val color: Argb, val dashed: Boolean)

    fun hasAnyTarget(): Boolean =
        shot.samples.any { s ->
            (Series.PRESSURE in series && (s.targetPressure ?: 0f) > 0f) ||
                (Series.FLOW in series && (s.targetFlow ?: 0f) > 0f) ||
                (Series.TEMPERATURE in series && s.targetTemperature != null) ||
                (Series.WEIGHT in series && s.targetWeight != null)
        }

    // Chart ---------------------------------------------------------------------------------

    fun drawChart(chart: Box) {
        val plot = Box(chart.left + 4f * u, chart.top + 4f * u, chart.right - 4f * u, chart.bottom - 2f * u)
        if (plot.width <= 0f || plot.height <= 0f) return
        drawGrid(plot)
        val x = { t: Long -> plot.left + (t.toFloat() / windowUs) * plot.width }

        // Built once per series: the areas and the lines over them use the same runs.
        val runs = CurveSeries.filter { it in series }.associateWith { curveRuns(it, plot, x) }
        if (style.lines.area > 0f) {
            for ((s, list) in runs) for (run in list) {
                out += AreaPrim(run, plot.bottom, seriesColor(s).timesAlpha(style.lines.area))
            }
        }
        if (showTargets) drawTargets(plot, x)
        for ((s, list) in runs) {
            val width = (if (s == Series.PRESSURE || s == Series.FLOW) style.lines.main else style.lines.secondary) * u
            for (run in list) {
                lineShadow(run, width)
                out += PolylinePrim(run, seriesColor(s), width)
            }
        }
        if (settings.phases) drawPhases(plot, x)
    }

    /** A soft shadow under a curve: two wider, fainter copies a little lower. */
    fun lineShadow(run: FloatArray, width: Float) {
        val sh = style.lines.shadow ?: return
        if (dark != null) return
        val dy = sh.dy * u
        val moved = FloatArray(run.size) { i -> if (i % 2 == 1) run[i] + dy else run[i] }
        out += PolylinePrim(moved, sh.color.timesAlpha(0.35f), width + sh.blur * u * 2f)
        out += PolylinePrim(moved, sh.color.timesAlpha(0.5f), width + sh.blur * u)
    }

    fun drawGrid(plot: Box) {
        if (style.grid.mode == GridMode.NONE) return
        val w = (OverlayMetrics.GRID_LINE * u).coerceAtLeast(1f)
        val dash = style.grid.dash?.let { Dash(it.on * u, it.off * u) }
        val color = style.grid.color
        for (i in 1..4) {
            val y = plot.top + plot.height * i / 5f
            out += PolylinePrim(floatArrayOf(plot.left, y, plot.right, y), color, w, dash)
        }
        if (style.grid.mode == GridMode.BOTH)
            for (i in 1..4) {
                val xx = plot.left + plot.width * i / 5f
                out += PolylinePrim(floatArrayOf(xx, plot.top, xx, plot.bottom), color, w, dash)
            }
    }

    fun yOf(s: Series, v: Float, plot: Box): Float {
        val (min, max) =
            when (s) {
                Series.WEIGHT -> 0f to weightMax
                Series.TEMPERATURE -> tempRange.start to tempRange.endInclusive
                else -> 0f to pressureFlowMax
            }
        val f = ((v.coerceIn(min, max) - min) / (max - min))
        return plot.bottom - f * plot.height
    }

    /** Gaps sorted by start, for walking alongside the samples. */
    val gaps = shot.gaps.sortedBy { it.startUs }

    /** A run as it is built, reused from run to run and series to series; only finished runs are copied out. */
    val buf = FloatBuf()

    /**
     * Smoothed, unless the style draws steps: a step is meant to look like one. Spans are split in reference pixels (×
     * [u]), so the export gets the same points as the preview, scaled — not more of them.
     */
    fun finish(run: FloatArray, plot: Box): FloatArray =
        if (style.lines.stepped) run else smoothRun(run, plot.top, plot.bottom, minSpanPx = SMOOTH_SPAN * u)

    /**
     * Whether a gap starts after [prevT] and by [t], or [t] falls inside one: the curve breaks there. [from] is the
     * first gap that ends after [prevT]; the caller keeps it, so a whole series walks the gaps once.
     */
    fun gapBetween(prevT: Long, t: Long, from: IntArray): Boolean {
        while (from[0] < gaps.size && gaps[from[0]].endUs <= prevT) from[0]++
        var j = from[0]
        while (j < gaps.size && gaps[j].startUs <= t) {
            val g = gaps[j]
            if (g.startUs > prevT || t < g.endUs) return true
            j++
        }
        return false
    }

    /** Polyline runs of one series up to the current time, broken at gaps and missing values. */
    fun curveRuns(s: Series, plot: Box, x: (Long) -> Float): List<FloatArray> {
        if (tUs < 0) return emptyList()
        val runs = ArrayList<FloatArray>(2)
        buf.clear()
        val gapAt = intArrayOf(0)
        var prevT = Long.MIN_VALUE
        var lastStepT = Long.MIN_VALUE
        var prevY = 0f
        fun flush() {
            if (buf.size >= 4) runs += finish(buf.toArray(), plot)
            buf.clear()
        }
        fun add(t: Long, v: Float?) {
            if (prevT != Long.MIN_VALUE && gapBetween(prevT, t, gapAt)) flush()
            prevT = t
            if (v == null) {
                flush()
                return
            }
            if (style.lines.stepped) {
                if (buf.size > 0 && t - lastStepT < OverlayMetrics.STEP_INTERVAL_US && t != tUs) return
                val q = kotlin.math.round(v / OverlayMetrics.STEP_QUANTUM) * OverlayMetrics.STEP_QUANTUM
                val y = yOf(s, q, plot)
                if (buf.size > 0) buf.add(x(t), prevY)
                buf.add(x(t), y)
                prevY = y
                lastStepT = t
            } else {
                buf.add(x(t), yOf(s, v, plot))
            }
        }
        var lastT = Long.MIN_VALUE
        for (sample in shot.samples) {
            if (sample.tUs > tUs) break
            add(sample.tUs, sample.value(s))
            lastT = sample.tUs
        }
        // The head of the curve at exactly the current time, between two samples, so playback moves it smoothly.
        val last = shot.samples.lastOrNull()
        if (last != null && tUs < last.tUs && lastT != Long.MIN_VALUE && lastT < tUs) {
            add(tUs, shot.sampleAt(tUs)?.value(s))
        }
        flush()
        return runs
    }

    fun drawTargets(plot: Box, x: (Long) -> Float) {
        val width = (style.lines.targetWidth ?: style.lines.secondary) * u
        val dash =
            when (style.lines.targets) {
                TargetLook.DASHED -> Dash(OverlayMetrics.TARGET_DASH.on * u, OverlayMetrics.TARGET_DASH.off * u)
                TargetLook.DOTTED -> Dash(1.5f * u, 3f * u)
                TargetLook.FAINT -> null
            }
        val fade = style.lines.targetOpacity * if (style.lines.targets == TargetLook.FAINT) 0.45f else 1f
        fun color(s: Series) = seriesColor(s).timesAlpha(fade)
        fun runs(s: Series, pick: (Sample) -> Float?): List<FloatArray> {
            val res = ArrayList<FloatArray>()
            buf.clear()
            var prevT = 0L
            var prevV: Float? = null
            for (sample in shot.samples) {
                val v = pick(sample)
                if (v == null || shot.gaps.any { sample.tUs in it }) {
                    if (buf.size >= 4) res += finish(buf.toArray(), plot)
                    buf.clear()
                    prevV = null
                    continue
                }
                // The profile runs ahead of the curve: it stops at the window's edge, cut where it crosses it.
                if (sample.tUs > windowUs) {
                    prevV?.let { pv ->
                        val f = (windowUs - prevT).toFloat() / (sample.tUs - prevT).coerceAtLeast(1)
                        buf.add(x(windowUs), yOf(s, pv + (v - pv) * f, plot))
                    }
                    break
                }
                buf.add(x(sample.tUs), yOf(s, v, plot))
                prevT = sample.tUs
                prevV = v
            }
            if (buf.size >= 4) res += finish(buf.toArray(), plot)
            buf.clear()
            return res
        }
        if (Series.PRESSURE in series)
            runs(Series.PRESSURE) { it.targetPressure?.takeIf { v -> v > 0f } }
                .forEach { out += PolylinePrim(it, color(Series.PRESSURE), width, dash) }
        if (Series.FLOW in series)
            runs(Series.FLOW) { it.targetFlow?.takeIf { v -> v > 0f } }
                .forEach { out += PolylinePrim(it, color(Series.FLOW), width, dash) }
        if (Series.TEMPERATURE in series)
            runs(Series.TEMPERATURE) { it.targetTemperature }
                .forEach { out += PolylinePrim(it, color(Series.TEMPERATURE), width, dash) }
        if (Series.WEIGHT in series) {
            val stop = shot.samples.lastOrNull { it.targetWeight != null }?.targetWeight
            if (stop != null) {
                val y = yOf(Series.WEIGHT, stop, plot)
                out += PolylinePrim(floatArrayOf(plot.left, y, plot.right, y), color(Series.WEIGHT), width, dash)
            }
        }
    }

    fun drawPhases(plot: Box, x: (Long) -> Float) {
        val size = OverlayMetrics.PHASE_SIZE * u
        // Two phases a second apart used to print their labels on top of each other. The line is
        // always drawn; the label is dropped when it would run into the one before it.
        var labelledTo = Float.NEGATIVE_INFINITY
        shot.phaseStartsUs.forEachIndexed { i, start ->
            if (start <= 0L || start > tUs || start > windowUs) return@forEachIndexed
            val xx = x(start)
            out +=
                PolylinePrim(floatArrayOf(xx, plot.top, xx, plot.bottom), style.phase.line, (1f * u).coerceAtLeast(1f))
            val label = labels.phase(i + 1)
            val left = xx + 2f * u
            val right = left + metrics.width(label, size, 400)
            if (left < labelledTo || right > plot.right) return@forEachIndexed
            labelledTo = right + 4f * u
            out +=
                TextPrim(
                    label,
                    left,
                    plot.top + metrics.ascent(size),
                    size,
                    400,
                    style.phase.label,
                    shadow = textShadow,
                )
        }
    }

    // Readings ------------------------------------------------------------------------------

    data class Cell(val reading: Reading, val label: String, val value: String, val unit: String, val target: String?)

    fun cells(): List<Cell> {
        val cur = shot.sampleAt(tUs.coerceAtLeast(0L))
        fun fmt(v: Float?) = v?.oneDecimal() ?: "–"
        fun target(v: Float?, unit: String) =
            if (showTargets && v != null) labels.targetValue(v.oneDecimal(), unit) else null
        val timeS = (tUs.coerceIn(0L, maxOf(shot.durationUs, 0L)) / 1_000_000f)
        return buildList {
            add(Cell(Reading.TIME, labels.time, timeS.oneDecimal(), labels.seconds, null))
            if (Series.WEIGHT in series)
                add(
                    Cell(
                        Reading.WEIGHT,
                        labels.weight,
                        fmt(cur?.weight),
                        labels.grams,
                        target(cur?.targetWeight, labels.grams),
                    )
                )
            if (Series.PRESSURE in series)
                add(
                    Cell(
                        Reading.PRESSURE,
                        labels.pressure,
                        fmt(cur?.pressure),
                        labels.bar,
                        target(cur?.targetPressure?.takeIf { it > 0f }, labels.bar),
                    )
                )
            if (Series.FLOW in series)
                add(
                    Cell(
                        Reading.FLOW,
                        labels.flow,
                        fmt(cur?.flow),
                        labels.mlPerS,
                        target(cur?.targetFlow?.takeIf { it > 0f }, labels.mlPerS),
                    )
                )
            if (Series.WEIGHT_FLOW in series)
                add(Cell(Reading.WEIGHT_FLOW, labels.weightFlow, fmt(cur?.weightFlow), labels.gPerS, null))
            if (Series.TEMPERATURE in series)
                add(
                    Cell(
                        Reading.TEMPERATURE,
                        labels.temperature,
                        fmt(cur?.temperature),
                        labels.celsius,
                        target(cur?.targetTemperature, labels.celsius),
                    )
                )
        }
    }

    fun drawReadings(area: Box) {
        val cells = cells()
        val gap = if (style.cells.look == CellLook.TILES && tiles != null) tiles.gap * u else OverlayMetrics.GAP * u
        val cellW = (area.width - gap * (cells.size - 1)) / cells.size
        if (cellW <= 0f || area.height <= 0f) return
        val labelSize = OverlayMetrics.LABEL_SIZE * u
        val targetSize = OverlayMetrics.TARGET_SIZE * u
        val baseValue =
            when {
                settings.layout == Layout.READINGS_ONLY -> OverlayMetrics.VALUE_SIZE_READINGS_ONLY
                cellW > 80f * u -> OverlayMetrics.VALUE_SIZE
                else -> OverlayMetrics.VALUE_SIZE_NARROW
            } * u
        val anyTarget = cells.any { it.target != null }
        val minValue = 9f * u
        val lh = { s: Float -> metrics.ascent(s) + metrics.descent(s) }
        val stack = { v: Float -> lh(labelSize) + lh(v) + if (anyTarget) lh(targetSize) else 0f }
        // A cell's round corners eat into it. However much room the stack leaves above and below decides how far in
        // from the sides the text must start to clear the curve at that height. A corner that would push the text in
        // past the usual padding shrinks the value first, until the stack clears it at 45°.
        val cellRadius = if (style.cells.look == CellLook.TILES) panel.radius * u else style.cells.radius * u
        val r = cellRadius.coerceIn(0f, minOf(cellW, area.height) / 2f)
        fun clearCurve(v: Float): Float {
            val dy = ((area.height - stack(v)) / 2f).coerceAtLeast(0f)
            return if (dy >= r) 0f else r - kotlin.math.sqrt((r * r - (r - dy) * (r - dy)).coerceAtLeast(0f))
        }
        var valueSize = baseValue
        // A short overlay would push the target line out of the rect: shrink the value instead.
        while (valueSize > minValue && stack(valueSize) > area.height) valueSize *= 0.92f
        if (clearCurve(valueSize) > OverlayMetrics.CELL_PAD * u) {
            val roomH = area.height - 2 * cornerInset(r, Box(0f, 0f, cellW, area.height))
            while (valueSize > minValue && stack(valueSize) > roomH) valueSize *= 0.92f
        }
        valueSize = valueSize.coerceAtLeast(minValue)
        val pad = maxOf(OverlayMetrics.CELL_PAD * u, clearCurve(valueSize))
        // One value size for the whole row so the cells line up: the largest that fits every cell. Smaller only leaves
        // more room above and below, so the padding still clears the corners.
        while (valueSize > minValue && cells.any { valueRowWidth(it, valueSize) > cellW - 2 * pad }) valueSize *= 0.92f
        valueSize = valueSize.coerceAtLeast(minValue)
        val stackH = stack(valueSize)
        val top = area.top + ((area.height - stackH) / 2f).coerceAtLeast(0f)

        cells.forEachIndexed { i, c ->
            val left = area.left + i * (cellW + gap)
            val cellBox = Box(left, area.top, left + cellW, area.bottom)
            drawCell(cellBox, c.reading)
            val x = left + pad
            var y = top
            val label = ellipsize(c.label, cellW - 2 * pad, labelSize, 400)
            out += TextPrim(label, x, y + metrics.ascent(labelSize), labelSize, 400, labelColor, shadow = textShadow)
            y += lh(labelSize)
            val valueBaseline = y + metrics.ascent(valueSize)
            out +=
                TextPrim(c.value, x, valueBaseline, valueSize, valueWeight, valueColor(c.reading), shadow = textShadow)
            val unitSize = unitSize(valueSize)
            val unitX = x + metrics.width(c.value, valueSize, valueWeight) + 0.25f * valueSize
            if (unitX + metrics.width(c.unit, unitSize, 400) <= left + cellW - pad / 2f) {
                out += TextPrim(c.unit, unitX, valueBaseline, unitSize, 400, labelColor, shadow = textShadow)
            }
            y += lh(valueSize)
            if (c.target != null) {
                val t = ellipsize(c.target, cellW - 2 * pad, targetSize, 400)
                out +=
                    TextPrim(
                        t,
                        x,
                        y + metrics.ascent(targetSize),
                        targetSize,
                        400,
                        targetTextColor,
                        shadow = textShadow,
                    )
            }
        }
    }

    val valueWeight: Int
        get() = style.cells.valueWeight

    fun drawCell(b: Box, reading: Reading) {
        val cs = style.cells
        val radius = cs.radius * u
        val bw = cs.borderWidth * u
        fun border(color: Argb) {
            if (color.alpha > 0f && bw > 0f) out += RectPrim(b.inset(bw / 2f), color, radius, stroke = bw)
        }
        when (cs.look) {
            CellLook.FILLED -> {
                if (cs.fill.alpha > 0f) out += RectPrim(b, cs.fill.bg(), radius)
                border(cs.border)
            }
            CellLook.OUTLINED -> border(if (cs.border.alpha > 0f) cs.border else cs.fill)
            CellLook.PLAIN -> {}
            CellLook.GLOSSY -> {
                val g = cs.gradient
                if (g != null)
                    out += RectPrim(b, g.first().color, radius, gradient = g.map { GradientStop(it.at, it.color.bg()) })
                else if (cs.fill.alpha > 0f) out += RectPrim(b, cs.fill.bg(), radius)
                border(cs.border)
            }
            CellLook.TILES -> drawTile(b, cs.tiles[reading] ?: panel.color)
        }
    }

    fun unitSize(valueSize: Float) = maxOf(8f * u, valueSize * 0.5f)

    fun valueRowWidth(c: Cell, size: Float): Float =
        metrics.width(c.value, size, valueWeight) + 0.25f * size + metrics.width(c.unit, unitSize(size), 400)

    // Text helpers --------------------------------------------------------------------------

    fun centeredBaseline(row: Box, size: Float): Float {
        val a = metrics.ascent(size)
        val d = metrics.descent(size)
        return row.top + (row.height - (a + d)) / 2f + a
    }

    /** The longest start of [text] that fits with an ellipsis, found by halving: log₂ n measurements, not n. */
    fun ellipsize(text: String, maxWidth: Float, size: Float, weight: Int): String {
        if (maxWidth <= 0f) return ""
        if (metrics.width(text, size, weight) <= maxWidth) return text
        fun cut(end: Int) = text.take(end).trimEnd() + "…"
        var fits = 0
        var tooLong = text.length
        while (tooLong - fits > 1) {
            val mid = (fits + tooLong) / 2
            if (metrics.width(cut(mid), size, weight) <= maxWidth) fits = mid else tooLong = mid
        }
        return if (fits == 0) "" else cut(fits)
    }

    fun seriesColor(s: Series): Argb =
        when (s) {
            Series.PRESSURE -> palette.pressure
            Series.FLOW -> palette.flow
            Series.WEIGHT -> palette.weight
            Series.WEIGHT_FLOW -> palette.weightFlow
            Series.TEMPERATURE -> palette.temperature
            Series.TARGETS -> targetTextColor
        }
}

/** Above this luminance behind the overlay, a style that adapts switches to its dark set. */
const val ADAPT_BRIGHT = 0.5f

/** Smoothing adds a point for every this many reference pixels of a span. */
private const val SMOOTH_SPAN = 3f

/** A growable float list without boxing, for building runs of points. */
internal class FloatBuf(capacity: Int = 256) {
    private var a = FloatArray(capacity)
    var size = 0
        private set

    fun add(x: Float, y: Float) {
        if (size + 2 > a.size) a = a.copyOf(maxOf(a.size * 2, size + 2))
        a[size++] = x
        a[size++] = y
    }

    fun clear() {
        size = 0
    }

    fun toArray(): FloatArray = a.copyOf(size)
}

/**
 * How far in from a rounded rect's sides a box must sit for its corners to stay inside the round corners: a corner of
 * radius r (capped at half the shorter side, as drawn) clears a point inset by r · (1 − 1/√2) on both axes.
 */
internal fun cornerInset(radius: Float, box: Box): Float {
    val r = radius.coerceIn(0f, minOf(box.width, box.height).coerceAtLeast(0f) / 2f)
    return r * (1f - 1f / kotlin.math.sqrt(2f))
}
