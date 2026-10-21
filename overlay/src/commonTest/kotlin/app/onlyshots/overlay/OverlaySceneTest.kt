package app.onlyshots.overlay

import app.onlyshots.core.model.Layout
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Series
import app.onlyshots.core.model.TimeRange
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.oneDecimal
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.overlay.scene.AreaPrim
import app.onlyshots.overlay.scene.BackdropPrim
import app.onlyshots.overlay.scene.Box
import app.onlyshots.overlay.scene.OverlayScene
import app.onlyshots.overlay.scene.PolylinePrim
import app.onlyshots.overlay.scene.RectPrim
import app.onlyshots.overlay.scene.TextPrim
import app.onlyshots.overlay.scene.buildOverlayScene
import app.onlyshots.overlay.scene.chartWindowUs
import app.onlyshots.overlay.scene.temperatureRange
import app.onlyshots.overlay.scene.visibleSeries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlaySceneTest {
    private val shot = SampleShots.zer0

    /** Steps with a fill under them, as a user can set up in the style editor. */
    private val Stepped =
        GraphStyles.Gaggiuino.copy(
            id = "u-steps",
            lines = GraphStyles.Gaggiuino.lines.copy(stepped = true, area = 0.32f),
        )
    private val preRollUs = 3_000_000L

    private fun scene(
        tShotS: Double,
        settings: OverlaySettings = OverlaySettings(),
        style: GraphStyle = GraphStyles.Gaggiuino,
        frameWidth: Float = 390f,
        s: app.onlyshots.core.model.Shot = shot,
        recipe: String? = null,
    ): OverlayScene =
        buildOverlayScene(
            s,
            videoTimeUs = preRollUs + (tShotS * 1_000_000).toLong(),
            shotStartUs = preRollUs,
            settings = settings,
            style = style,
            title = "Zer0 · Gaggiuino",
            frameWidth = frameWidth,
            frameHeight = frameWidth * 16f / 9f,
            metrics = FakeTextMetrics,
            labels = EnglishLabels,
            recipe = recipe,
        )

    private fun OverlayScene.texts() = prims.filterIsInstance<TextPrim>().map { it.text }

    private fun OverlayScene.lines() = prims.filterIsInstance<PolylinePrim>()

    @Test
    fun deterministic() {
        val a = scene(20.0)
        val b = scene(20.0)
        // Every prim, areas included: polylines and areas compare their points.
        assertEquals(a.prims, b.prims)
        assertEquals(scene(20.0, style = Stepped).prims, scene(20.0, style = Stepped).prims)
    }

    @Test
    fun anatomyHeaderChartReadings() {
        val texts = scene(20.0).texts()
        assertTrue("Zer0 · Gaggiuino" in texts, texts.toString())
        // Legend: one entry per selected series plus a single target entry.
        assertEquals(
            listOf("pressure", "flow", "weight", "target"),
            texts.filter { it in setOf("pressure", "flow", "weight", "target", "temp") }.take(4),
        )
        // Readings: time first, then weight, pressure, flow.
        val labels = texts.filter { it in setOf("time", "weight", "pressure", "flow") }
        assertEquals(listOf("time", "weight", "pressure", "flow"), labels.takeLast(4))
    }

    @Test
    fun readingsShowValueAtCurrentTimeWithUnits() {
        val texts = scene(20.0).texts()
        assertTrue("20.0" in texts, texts.toString())
        val at20 = shot.samples.first { it.tUs == 20_000_000L }
        assertTrue(at20.pressure!!.oneDecimal() in texts)
        assertTrue("bar" in texts && "ml/s" in texts && "g" in texts && "s" in texts)
        assertTrue(texts.any { it.startsWith("target ") && it.endsWith(" g") }, "stop weight target")
        assertFalse(texts.any { it.startsWith("target") && it.endsWith("bar") }, "no pressure target in a flow profile")
    }

    @Test
    fun curvesStopAtTheCurrentTime() {
        val plotRight = scene(40.0).lines().maxOf { l -> (0 until l.pointCount).maxOf { l.points[2 * it] } }
        val early = scene(10.0)
        val solid = early.lines().filter { it.dash == null && it.pointCount > 2 }
        val maxX = solid.maxOf { l -> (0 until l.pointCount).maxOf { l.points[2 * it] } }
        assertTrue(maxX < plotRight * 0.5f, "curve at 10 s reaches $maxX of $plotRight")
    }

    @Test
    fun targetsSpanTheWholeShotDashed() {
        val early = scene(5.0)
        val dashed = early.lines().filter { it.dash != null && it.pointCount > 2 }
        assertTrue(dashed.isNotEmpty(), "flow target drawn")
        val lastX = dashed.maxOf { it.points[it.points.size - 2] }
        val solidMax =
            early.lines().filter { it.dash == null && it.pointCount > 2 }.maxOf { it.points[it.points.size - 2] }
        assertTrue(lastX > solidMax * 3f)
        assertEquals(5f, dashed.first().dash!!.on)
        assertEquals(4f, dashed.first().dash!!.off)
    }

    @Test
    fun lineWidthsScaleWithFrame() {
        val small = scene(20.0, frameWidth = 390f).lines().filter { it.dash == null }.maxOf { it.width }
        val big = scene(20.0, frameWidth = 1080f).lines().filter { it.dash == null }.maxOf { it.width }
        assertEquals(2.6f, small, 1e-4f)
        assertEquals(2.6f * 1080f / 390f, big, 1e-3f)
    }

    @Test
    fun exportFrameIsAScaledCopyOfPreview() {
        val preview = scene(25.0, frameWidth = 390f)
        val export = scene(25.0, frameWidth = 1080f)
        assertEquals(preview.prims.size, export.prims.size)
        val k = 1080f / 390f
        preview.prims.zip(export.prims).forEach { (p, e) ->
            when (p) {
                is TextPrim -> {
                    e as TextPrim
                    assertEquals(p.text, e.text)
                    assertEquals(p.x * k, e.x, 0.05f)
                    assertEquals(p.baseline * k, e.baseline, 0.05f)
                }
                is RectPrim -> {
                    e as RectPrim
                    assertEquals(p.box.left * k, e.box.left, 0.05f)
                    assertEquals(p.box.bottom * k, e.box.bottom, 0.05f)
                }
                is BackdropPrim -> {
                    e as BackdropPrim
                    assertEquals(p.box.left * k, e.box.left, 0.05f)
                    assertEquals(p.blur * k, e.blur, 0.05f)
                }
                is PolylinePrim -> {
                    e as PolylinePrim
                    assertEquals(p.points.size, e.points.size)
                    p.points.indices.forEach { assertEquals(p.points[it] * k, e.points[it], 0.05f) }
                }
                is AreaPrim -> {
                    e as AreaPrim
                    assertEquals(p.points.size, e.points.size)
                    assertEquals(p.baseline * k, e.baseline, 0.05f)
                    p.points.indices.forEach { assertEquals(p.points[it] * k, e.points[it], 0.05f) }
                }
            }
        }
    }

    @Test
    fun phaseMarkersAppearOncePassed() {
        assertFalse("phase 2" in scene(8.0).texts())
        assertTrue("phase 2" in scene(9.0).texts())
        assertFalse("phase 3" in scene(9.0).texts())
        assertTrue("phase 3" in scene(30.0).texts())
    }

    @Test
    fun beforeTheShotNoCurvesButReadings() {
        val pre = scene(-2.0)
        assertTrue(pre.lines().none { it.dash == null && it.pointCount > 2 })
        assertTrue("0.0" in pre.texts())
    }

    @Test
    fun layouts() {
        val graphOnly = scene(20.0, OverlaySettings(layout = Layout.GRAPH_ONLY)).texts()
        assertFalse("time" in graphOnly)
        val readingsOnly = scene(20.0, OverlaySettings(layout = Layout.READINGS_ONLY)).texts()
        assertTrue("time" in readingsOnly)
        assertFalse("Zer0 · Gaggiuino" in readingsOnly)
        assertTrue(scene(20.0, OverlaySettings(layout = Layout.READINGS_ONLY)).lines().isEmpty())
    }

    @Test
    fun minimumSizeDropsChartBeforeReadings() {
        val tiny = OverlaySettings(rect = NormRect(0.1f, 0.1f, NormRect.MIN_W, NormRect.MIN_H))
        val sc = scene(20.0, tiny)
        assertTrue("20.0" in sc.texts(), sc.texts().toString())
        sc.prims.forEach { p ->
            if (p is RectPrim) {
                assertTrue(
                    p.box.right <= sc.bounds.right + 0.01f && p.box.bottom <= sc.bounds.bottom + 0.01f,
                    "inside bounds: $p",
                )
            }
        }
    }

    @Test
    fun opacityFadesTheBackgroundOnly() {
        val full = scene(20.0)
        val half = scene(20.0, OverlaySettings().withOpacity(0.5f))
        // The panel and the other filled backgrounds fade with the setting…
        val fills = { sc: OverlayScene -> sc.prims.filterIsInstance<RectPrim>().filter { it.stroke == 0f } }
        fills(full).zip(fills(half)).forEach { (f, h) -> assertEquals(f.color.alpha * 0.5f, h.color.alpha, 0.01f) }
        // …while curves and text keep their full colour.
        assertEquals(full.lines().map { it.color }, half.lines().map { it.color })
        assertEquals(
            full.prims.filterIsInstance<TextPrim>().map { it.color },
            half.prims.filterIsInstance<TextPrim>().map { it.color },
        )
    }

    @Test
    fun atZeroOnlyCurvesAndReadingsRemain() {
        val clear = scene(20.0, OverlaySettings().withOpacity(0f))
        assertTrue(clear.prims.filterIsInstance<RectPrim>().filter { it.stroke == 0f }.all { it.color.alpha == 0f })
        assertTrue(clear.lines().any { it.color.alpha > 0.99f })
        assertTrue("20.0" in clear.texts())
    }

    @Test
    fun seriesWithoutDataAreNotDrawn() {
        val noWeight =
            shot.copy(samples = shot.samples.map { it.copy(weight = null, weightFlow = null, targetWeight = null) })
        assertEquals(listOf(Series.PRESSURE, Series.FLOW), visibleSeries(noWeight, OverlaySettings.DefaultSeries))
        assertFalse("weight" in scene(20.0, s = noWeight).texts())
    }

    @Test
    fun beforeTheFirstSampleEverySelectedSeriesWaits() {
        val waiting = shot.copy(samples = emptyList())
        assertEquals(
            listOf(Series.PRESSURE, Series.FLOW, Series.WEIGHT),
            visibleSeries(waiting, OverlaySettings.DefaultSeries),
        )
        assertTrue("weight" in scene(0.0, s = waiting).texts())
    }

    @Test
    fun gapsBreakCurves() {
        val gapped = shot.copy(gaps = listOf(TimeRange(10_000_000, 12_000_000)))
        val settings = OverlaySettings(series = setOf(Series.PRESSURE))
        val runs = scene(20.0, settings, s = gapped).lines().filter { it.dash == null && it.pointCount > 2 }
        assertEquals(2, runs.size)
        assertTrue("–" in scene(11.0, settings, s = gapped).texts())
    }

    @Test
    fun steppedStyleDrawsStepsAndFills() {
        val sc = scene(20.0, style = Stepped)
        assertTrue(sc.prims.any { it is AreaPrim })
        val pressure = sc.lines().filter { it.dash == null && it.pointCount > 4 }.last()
        // Every other segment is vertical or horizontal.
        for (i in 1 until pressure.pointCount) {
            val dx = pressure.points[2 * i] - pressure.points[2 * i - 2]
            val dy = pressure.points[2 * i + 1] - pressure.points[2 * i - 1]
            assertTrue(dx == 0f || dy == 0f, "segment $i is diagonal")
        }
    }

    @Test
    fun blurStylesAskForTheVideoToBeBlurredFirst() {
        val aero = scene(25.0, style = GraphStyles.Aero)
        assertTrue(aero.prims.first { it is BackdropPrim || (it is RectPrim && it.gradient != null) } is BackdropPrim)
        assertEquals(1, aero.prims.count { it is BackdropPrim })
        // The gloss is a gradient with a hard step.
        assertTrue(aero.prims.any { it is RectPrim && it.gradient?.size == 4 })
        // Metro: the header and chart tile, plus one tile per reading.
        val metro = scene(25.0, style = GraphStyles.Metro)
        // Time, weight, pressure, flow: the default series.
        assertEquals(1 + 4, metro.prims.count { it is BackdropPrim })
        assertEquals(0, scene(25.0, style = GraphStyles.Gaggiuino).prims.count { it is BackdropPrim })
    }

    @Test
    fun glassIsOneBackdropWithItsFrostAndCorner() {
        val settings = app.onlyshots.core.model.style.GlassSettings(frost = 12f, corner = 20f, refraction = 0.3f)
        val glass =
            GraphStyles.Gaggiuino.copy(
                panel =
                    GraphStyles.Gaggiuino.panel.copy(
                        material = app.onlyshots.core.model.style.Material.GLASS,
                        glass = settings,
                        color = rgb(0xFFFFFF),
                        opacity = 0.14f,
                    )
            )
        val small = scene(25.0, style = glass).prims
        val backdrop = small.filterIsInstance<BackdropPrim>().single()
        assertEquals(settings, backdrop.glass)
        assertEquals(12f, backdrop.blur, 1e-4f)
        // The corner is a share of the shorter side, as in the Liquid demo.
        assertEquals(0.2f * minOf(backdrop.box.width, backdrop.box.height), backdrop.radius, 1e-3f)
        assertEquals(0.14f, ((backdrop.tint ushr 24) and 0xFF) / 255f, 0.01f)
        // The glass draws its own rim and tint: no panel rect or outline around it.
        assertTrue(small.none { it is RectPrim && it.box == backdrop.box })
        val big = scene(25.0, style = glass, frameWidth = 1080f).prims.filterIsInstance<BackdropPrim>().single()
        assertEquals(12f * 1080f / 390f, big.blur, 1e-3f)
        assertEquals(backdrop.radius * 1080f / 390f, big.radius, 0.05f)
    }

    /** A 40 s shot sampled every 0.25 s, its values from [at] (seconds → sample). */
    private fun synthetic(at: (Double) -> Sample): app.onlyshots.core.model.Shot {
        val samples = (0..160).map { at(it * 0.25) }
        return shot.copy(
            samples = samples,
            durationUs = samples.last().tUs,
            phaseStartsUs = listOf(0L),
            gaps = emptyList(),
        )
    }

    private fun Double.us() = (this * 1_000_000).toLong()

    private fun curve(sc: app.onlyshots.overlay.scene.OverlayScene, color: Int): FloatArray =
        sc.prims
            .filterIsInstance<PolylinePrim>()
            .filter { it.color == color && it.dash == null }
            .maxBy { it.pointCount }
            .points

    @Test
    fun theWeightAxisGrowsInsteadOfFlatlining() {
        // A lungo: weight climbs to 72 g, past the 60 g the axis starts at.
        val lungo = synthetic { t -> Sample(t.us(), pressure = 9f, flow = 2f, weight = (t * 1.8).toFloat()) }
        val ys = curve(scene(40.0, s = lungo), GraphStyles.Gaggiuino.series.weight).filterIndexed { i, _ -> i % 2 == 1 }
        val tail = ys.takeLast(12)
        assertTrue(tail.zipWithNext().all { (a, b) -> b < a }, "weight still climbing at the end, not flat: $tail")
    }

    @Test
    fun aScaleShotsWeightAxisFollowsTheWeight() {
        // A scale's shot: weight and weight flow only, no target. 36 g never reaches the 60 g a machine's axis
        // starts at, so the axis must follow the weight for the line to fill the chart as it pours.
        val scale = synthetic { t -> Sample(t.us(), weight = (t * 0.9).toFloat(), weightFlow = 0.9f) }
        val weight = GraphStyles.Gaggiuino.series.weight
        val settings = OverlaySettings(series = setOf(Series.WEIGHT, Series.WEIGHT_FLOW))
        fun lastY(at: Double) = curve(scene(at, settings, s = scale), weight).last()
        // The newest point sits at the same height at 20 g and at 36 g: the axis grew with it.
        assertEquals(lastY(22.2), lastY(40.0), 2f)
        // Early on it climbs far higher than the same pour on a machine's axis (a target, so 0-60 g) would.
        val machine = synthetic { t ->
            Sample(t.us(), weight = (t * 0.9).toFloat(), weightFlow = 0.9f, targetWeight = 36f)
        }
        fun rise(s: app.onlyshots.core.model.Shot) =
            curve(scene(5.0, settings, s = s), weight)
                .filterIndexed { i, _ -> i % 2 == 1 }
                .let { it.first() - it.last() }
        assertTrue(rise(scale) > 3 * rise(machine), "scale ${rise(scale)} px against machine ${rise(machine)} px")
    }

    @Test
    fun aScalesWeightFlowAboveTwelveIsNotClipped() {
        val gush = synthetic { t ->
            val wf = if (t <= 3.0) (t / 3.0 * 15).toFloat() else maxOf(1f, (15 - (t - 3.0) * 12).toFloat())
            Sample(t.us(), weight = (t * 2).toFloat(), weightFlow = wf)
        }
        val settings = OverlaySettings(series = setOf(Series.WEIGHT, Series.WEIGHT_FLOW))
        val ys =
            curve(scene(10.0, settings, s = gush), GraphStyles.Gaggiuino.series.weightFlow).filterIndexed { i, _ ->
                i % 2 == 1
            }
        val top = ys.min()
        assertEquals(1, ys.count { kotlin.math.abs(it - top) < 0.01f }, "the peak is a point, not a clipped plateau")
    }

    @Test
    fun aFlowSpikeAboveTwelveIsNotClipped() {
        val spike = synthetic { t ->
            val flow = if (t <= 3.0) (t / 3.0 * 14).toFloat() else maxOf(2f, (14 - (t - 3.0) * 12).toFloat())
            Sample(t.us(), pressure = 6f, flow = flow, weight = (t * 1.0).toFloat())
        }
        val ys = curve(scene(10.0, s = spike), GraphStyles.Gaggiuino.series.flow).filterIndexed { i, _ -> i % 2 == 1 }
        val top = ys.min()
        assertEquals(1, ys.count { kotlin.math.abs(it - top) < 0.01f }, "the peak is a point, not a clipped plateau")
    }

    @Test
    fun targetsStopAtTheEdgeOfAShorterWindow() {
        // At 10 s the window is 30 s; the 40 s target profile must not run past the graph.
        val sc = scene(10.0)
        val right = OverlaySettings().rect.right * 390f
        for (p in sc.prims.filterIsInstance<PolylinePrim>()) {
            for (i in 0 until p.pointCount) assertTrue(p.points[i * 2] <= right + 0.01f, "a line runs past the graph")
        }
    }

    @Test
    fun theLegendCanBeHidden() {
        val legendWords = setOf("pressure", "flow", "weight", "target")
        // Graph only: the readings' labels share the legend's words.
        fun texts(legend: Boolean) =
            scene(25.0, settings = OverlaySettings(layout = Layout.GRAPH_ONLY, legend = legend))
                .prims
                .filterIsInstance<TextPrim>()
                .map { it.text }
        assertTrue(texts(legend = true).containsAll(legendWords))
        val hidden = texts(legend = false)
        assertTrue(hidden.none { it in legendWords }, "legend still drawn: $hidden")
        assertTrue("Zer0 · Gaggiuino" in hidden)
    }

    @Test
    fun roundCornersNeverCutTheContent() {
        // A capsule panel and round data boxes: every piece of text starts inside the shape it sits in.
        val round =
            GraphStyles.LiquidGlass.copy(
                panel =
                    GraphStyles.LiquidGlass.panel.copy(glass = GraphStyles.LiquidGlass.panel.glass.copy(corner = 50f)),
                cells = GraphStyles.LiquidGlass.cells.copy(radius = 40f),
            )
        val prims = scene(25.0, style = round).prims
        val panel = prims.filterIsInstance<BackdropPrim>().single()
        val cells = prims.filterIsInstance<RectPrim>().filter { it.stroke == 0f && it.radius == 40f }
        assertEquals(4, cells.size)
        for (t in prims.filterIsInstance<TextPrim>()) {
            for (y in listOf(t.baseline - FakeTextMetrics.ascent(t.size), t.baseline)) {
                assertTrue(inside(t.x, y, panel.box, panel.radius), "'${t.text}' at ${t.x}, $y is outside the panel")
                cells
                    .firstOrNull { t.x >= it.box.left && t.x <= it.box.right && y >= it.box.top && y <= it.box.bottom }
                    ?.let { assertTrue(inside(t.x, y, it.box, it.radius), "'${t.text}' is cut by its box's corner") }
            }
        }
    }

    /** Whether ([x], [y]) is inside [box] with corners of [radius], capped at half the shorter side. */
    private fun inside(x: Float, y: Float, box: Box, radius: Float): Boolean {
        if (x < box.left || x > box.right || y < box.top || y > box.bottom) return false
        val r = minOf(radius, box.width / 2f, box.height / 2f)
        val cx = x.coerceIn(box.left + r, box.right - r)
        val cy = y.coerceIn(box.top + r, box.bottom - r)
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r + 0.01f
    }

    @Test
    fun minimalTurnsDarkOverABrightFrame() {
        val dim = scene(25.0, style = GraphStyles.Minimal)
        val bright =
            buildOverlayScene(
                shot,
                videoTimeUs = preRollUs + 25_000_000L,
                shotStartUs = preRollUs,
                settings = OverlaySettings(),
                style = GraphStyles.Minimal,
                title = "Zer0 · Gaggiuino",
                frameWidth = 390f,
                frameHeight = 390f * 16f / 9f,
                metrics = FakeTextMetrics,
                labels = EnglishLabels,
                backdropLuma = 0.9f,
            )
        val darkText = GraphStyles.Minimal.adapt.dark!!.text
        assertTrue(dim.prims.filterIsInstance<TextPrim>().none { it.color == darkText })
        assertTrue(
            bright.prims.filterIsInstance<TextPrim>().all { it.color == darkText || it.text.startsWith("phase") }
        )
    }

    @Test
    fun minimalGetsAScrimOnABusyMidToneFrame() {
        fun at(luma: Float) =
            buildOverlayScene(
                shot,
                videoTimeUs = preRollUs + 25_000_000L,
                shotStartUs = preRollUs,
                settings = OverlaySettings(),
                style = GraphStyles.Minimal,
                title = "Zer0 · Gaggiuino",
                frameWidth = 390f,
                frameHeight = 390f * 16f / 9f,
                metrics = FakeTextMetrics,
                labels = EnglishLabels,
                backdropLuma = luma,
            )
        assertTrue(at(0.02f).prims.none { it is RectPrim })
        assertTrue(at(0.4f).prims.any { it is RectPrim })
    }

    @Test
    fun everyStyleBuilds() {
        for (style in GraphStyles.all) {
            val sc = scene(25.0, style = style)
            assertTrue(sc.prims.isNotEmpty(), style.id)
            assertTrue("time" in sc.texts(), style.id)
        }
    }

    @Test
    fun scales() {
        // 30 s to begin with, then the shot so far plus 2 s, smoothly; after the shot, the whole of it.
        assertEquals(30_000_000L, chartWindowUs(shot, 10_000_000))
        assertEquals(32_000_000L, chartWindowUs(shot, 30_000_000))
        assertEquals(shot.durationUs + 2_000_000L, chartWindowUs(shot, shot.durationUs))
        assertEquals(shot.durationUs + 2_000_000L, chartWindowUs(shot, shot.durationUs + 9_000_000))
        val windows = (0..60).map { chartWindowUs(shot, it * 1_000_000L) }
        assertEquals(windows.sorted(), windows, "the time axis never shrinks")
        val r = temperatureRange(shot)
        assertEquals(94.6f - 2.5f, r.start, 1e-4f)
        assertEquals(95.4f + 2.5f, r.endInclusive, 1e-4f)
    }

    @Test
    fun pressureClampsAtTwelveBar() {
        val spiky = shot.copy(samples = shot.samples.map { it.copy(pressure = 20f) })
        val settings = OverlaySettings(series = setOf(Series.PRESSURE))
        val sc = scene(20.0, settings, s = spiky)
        val line = sc.lines().first { it.dash == null && it.pointCount > 2 }
        val minY = (0 until line.pointCount).minOf { line.points[2 * it + 1] }
        assertTrue(minY >= sc.bounds.top, "pressure stays inside the chart")
    }

    @Test
    fun emptyShotStillDrawsPanelAndReadings() {
        val empty = shot.copy(samples = emptyList<Sample>(), durationUs = 0)
        val sc = scene(0.0, s = empty)
        assertTrue("time" in sc.texts())
    }

    @Test
    fun aRecipeLineIsDrawnUnderTheTitle() {
        val plain = scene(10.0).prims.filterIsInstance<TextPrim>()
        val withRecipe = scene(10.0, recipe = "18.0 g → 36.4 g · 1:2.0 · Niche Zero").prims.filterIsInstance<TextPrim>()

        val line = withRecipe.single { it.text.startsWith("18.0 g") }
        val title = withRecipe.single { it.text.startsWith("Zer0") }
        assertTrue(line.baseline > title.baseline, "the recipe sits under the title")
        assertEquals(plain.size + 1, withRecipe.size, "nothing else was added or dropped")
    }

    @Test
    fun everyFormatDrawsTheGraphAtTheSameSizeAgainstItsShortSide() {
        fun frame(format: VideoFormat): OverlayScene {
            val size = Quality.HD_1080.size(format)
            return buildOverlayScene(
                shot,
                videoTimeUs = preRollUs + 20_000_000L,
                shotStartUs = preRollUs,
                settings = OverlaySettings(rect = NormRect.default(format)),
                style = GraphStyles.Gaggiuino,
                title = "Zer0 · Gaggiuino",
                frameWidth = size.width.toFloat(),
                frameHeight = size.height.toFloat(),
                metrics = FakeTextMetrics,
                labels = EnglishLabels,
            )
        }
        fun textSizes(scene: OverlayScene) = scene.prims.filterIsInstance<TextPrim>().associate { it.text to it.size }
        val portrait = frame(VideoFormat.PORTRAIT_9_16)
        assertTrue("time" in textSizes(portrait), textSizes(portrait).keys.toString())
        for (f in VideoFormat.entries) {
            val scene = frame(f)
            assertEquals(textSizes(portrait)["time"], textSizes(scene)["time"], "$f")
            val size = Quality.HD_1080.size(f)
            // Every line of the graph inside the frame.
            for (line in scene.lines()) {
                for (i in 0 until line.pointCount) {
                    assertTrue(
                        line.points[2 * i] in -1f..size.width + 1f && line.points[2 * i + 1] in -1f..size.height + 1f,
                        "$f",
                    )
                }
            }
        }
    }

    @Test
    fun phaseLinesCanBeSwitchedOff() {
        val phaseLine = GraphStyles.Gaggiuino.phase.line
        val on = scene(30.0)
        assertTrue(on.lines().any { it.color == phaseLine })
        val off = scene(30.0, settings = OverlaySettings(phases = false))
        assertTrue(off.lines().none { it.color == phaseLine }, "no phase lines")
        assertTrue(off.texts().none { it.startsWith("phase ") }, "no phase names")
    }

    @Test
    fun targetLinesTakeTheirOwnWeight() {
        // The targets are the dashed lines in a series' colour (the grid's dashes are its own colour).
        fun targetWidths(style: GraphStyle): Set<Float> {
            val colors = with(style.series) { setOf(pressure, flow, weight, weightFlow, temperature) }
            return scene(30.0, settings = OverlaySettings(series = Series.entries.toSet()), style = style)
                .lines()
                .filter { it.dash != null && it.color in colors }
                .map { it.width }
                .toSet()
        }
        assertEquals(setOf(GraphStyles.Gaggiuino.lines.secondary), targetWidths(GraphStyles.Gaggiuino))
        val heavy = GraphStyles.Gaggiuino.copy(lines = GraphStyles.Gaggiuino.lines.copy(targetWidth = 3.5f))
        assertEquals(setOf(3.5f), targetWidths(heavy))
    }

    @Test
    fun phaseLabelsThatWouldCollideAreDropped() {
        val crowded = shot.copy(phaseStartsUs = listOf(0L, 6_000_000L, 6_200_000L, 20_000_000L))

        val labels = scene(30.0, s = crowded).prims.filterIsInstance<TextPrim>().filter { it.text.startsWith("phase ") }
        val lines = scene(30.0, s = crowded).prims.filterIsInstance<PolylinePrim>()

        assertEquals(listOf("phase 2", "phase 4"), labels.map { it.text }, "the crowded label is dropped")
        assertTrue(lines.isNotEmpty(), "every phase still draws its line")
    }
}
