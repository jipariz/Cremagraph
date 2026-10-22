package app.onlyshots.overlay.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow as ComposeShadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.style.GlassSettings
import app.onlyshots.overlay.GraphStyle
import app.onlyshots.overlay.OverlayLabels
import app.onlyshots.overlay.glass.toHaze
import app.onlyshots.overlay.scene.AreaPrim
import app.onlyshots.overlay.scene.BackdropPrim
import app.onlyshots.overlay.scene.OverlayScene
import app.onlyshots.overlay.scene.PolylinePrim
import app.onlyshots.overlay.scene.RectPrim
import app.onlyshots.overlay.scene.TextAlign
import app.onlyshots.overlay.scene.TextMetrics
import app.onlyshots.overlay.scene.TextPrim
import app.onlyshots.overlay.scene.buildOverlayScene
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile as HazeRefractionProfile
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.math.roundToInt

/**
 * Text sizes in the scene are px; measuring at density 1 makes 1.sp == 1 px. The font scale is pinned to 1 on purpose:
 * the overlay must land on the exported frame pixel for pixel, so the system font size changes the app's own text and
 * never the burnt-in graph.
 */
internal val PixelDensity = Density(1f, 1f)

private fun textStyle(
    fontFamily: FontFamily,
    size: Float,
    weight: Int,
    color: Int = 0,
    shadow: app.onlyshots.overlay.Shadow? = null,
) =
    TextStyle(
        fontFamily = fontFamily,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        color = Color(color),
        shadow = shadow?.let { ComposeShadow(Color(it.color), Offset(0f, it.dy), it.blur) },
    )

private fun TextMeasurer.line(text: String, style: TextStyle): TextLayoutResult =
    measure(text, style, overflow = TextOverflow.Clip, softWrap = false, maxLines = 1, density = PixelDensity)

/**
 * [TextMetrics] backed by the Compose text measurer, so layout uses the style's real font metrics. Keep one across
 * frames: a scene asks for the same few dozen widths every frame, and the vertical metrics of a size never change, so
 * both are cached here rather than measured again (the measurer's own cache holds only a handful of layouts).
 */
class ComposeTextMetrics(private val measurer: TextMeasurer, private val fontFamily: FontFamily) : TextMetrics {
    private val widths = HashMap<WidthKey, Float>()
    private val verticals = HashMap<Float, Pair<Float, Float>>()

    override fun width(text: String, size: Float, weight: Int): Float {
        val key = WidthKey(text, size, weight)
        widths[key]?.let {
            return it
        }
        if (widths.size >= MAX_WIDTHS) widths.clear()
        return measurer.line(text, textStyle(fontFamily, size, weight)).size.width.toFloat().also { widths[key] = it }
    }

    override fun ascent(size: Float): Float = vertical(size).first

    override fun descent(size: Float): Float = vertical(size).second

    private fun vertical(size: Float): Pair<Float, Float> =
        verticals.getOrPut(size) {
            val r = measurer.line("Hg", textStyle(fontFamily, size, 400))
            r.firstBaseline to r.size.height - r.firstBaseline
        }

    private data class WidthKey(val text: String, val size: Float, val weight: Int)

    private companion object {
        /** Enough for every string of a few seconds of a running shot; past it the cache starts over. */
        const val MAX_WIDTHS = 512
    }
}

/** Layouts the text measurer keeps; the default is too small for one overlay frame's labels and readings. */
const val OVERLAY_TEXT_CACHE = 64

/**
 * Draws the overlay for video time [videoTimeUs]. The receiver's size is the whole 9:16 video frame; the overlay lands
 * at [OverlaySettings.rect]. This is the only drawing code for the graph: live preview, editor and export all come
 * through here.
 */
fun DrawScope.drawOverlay(
    shot: Shot,
    videoTimeUs: Long,
    shotStartUs: Long,
    settings: OverlaySettings,
    style: GraphStyle,
    title: String,
    textMeasurer: TextMeasurer,
    fonts: OverlayFonts,
    labels: OverlayLabels,
    recipe: String? = null,
    metrics: ComposeTextMetrics = ComposeTextMetrics(textMeasurer, fonts.of(style.text.font)),
) {
    val scene =
        buildOverlayScene(
            shot,
            videoTimeUs,
            shotStartUs,
            settings,
            style,
            title,
            frameWidth = size.width,
            frameHeight = size.height,
            metrics = metrics,
            labels = labels,
            recipe = recipe,
        )
    drawScene(scene, textMeasurer, fonts.of(style.text.font))
}

fun DrawScope.drawScene(scene: OverlayScene, textMeasurer: TextMeasurer, fontFamily: FontFamily) {
    // One path for the whole frame, emptied before each curve, instead of one per curve.
    val path = Path()
    for (p in scene.prims) when (p) {
        is RectPrim -> {
            val style = if (p.stroke > 0f) Stroke(width = p.stroke) else androidx.compose.ui.graphics.drawscope.Fill
            val g = p.gradient
            if (g != null) {
                drawRoundRect(
                    brush =
                        Brush.verticalGradient(
                            *g.map { it.at to Color(it.color) }.toTypedArray(),
                            startY = p.box.top,
                            endY = p.box.bottom,
                        ),
                    topLeft = Offset(p.box.left, p.box.top),
                    size = Size(p.box.width, p.box.height),
                    cornerRadius = CornerRadius(p.radius),
                    style = style,
                )
            } else {
                drawRoundRect(
                    color = Color(p.color),
                    topLeft = Offset(p.box.left, p.box.top),
                    size = Size(p.box.width, p.box.height),
                    cornerRadius = CornerRadius(p.radius),
                    style = style,
                )
            }
        }
        // The blur behind is drawn by whoever holds the video: Haze in the preview, the exporter in a frame.
        is BackdropPrim -> {}
        is PolylinePrim -> {
            if (p.pointCount < 2) continue
            path.rewind()
            if (p.pointCount == 2 && p.width <= 1.5f) {
                // A hairline grid or phase line: on the pixel grid, so it stays one sharp pixel instead of two faint
                // ones. Done here, not in the scene, so the export stays a scaled copy of the preview.
                path.moveTo(snap(p.points[0]), snap(p.points[1]))
                path.lineTo(snap(p.points[2]), snap(p.points[3]))
            } else {
                path.moveTo(p.points[0], p.points[1])
                for (i in 1 until p.pointCount) path.lineTo(p.points[2 * i], p.points[2 * i + 1])
            }
            drawPath(
                path = path,
                color = Color(p.color),
                style =
                    Stroke(
                        width = p.width,
                        cap = if (p.dash == null) StrokeCap.Round else StrokeCap.Butt,
                        join = StrokeJoin.Round,
                        pathEffect = p.dash?.let { PathEffect.dashPathEffect(floatArrayOf(it.on, it.off)) },
                    ),
            )
        }
        is AreaPrim -> {
            val n = p.points.size / 2
            if (n < 2) continue
            path.rewind()
            path.moveTo(p.points[0], p.baseline)
            for (i in 0 until n) path.lineTo(p.points[2 * i], p.points[2 * i + 1])
            path.lineTo(p.points[2 * (n - 1)], p.baseline)
            path.close()
            drawPath(path, Color(p.color))
        }
        is TextPrim -> {
            val layout = textMeasurer.line(p.text, textStyle(fontFamily, p.size, p.weight, p.color, p.shadow))
            val w = layout.size.width
            val x =
                when (p.align) {
                    TextAlign.START -> p.x
                    TextAlign.CENTER -> p.x - w / 2f
                    TextAlign.END -> p.x - w
                }
            drawText(layout, topLeft = Offset(x, p.baseline - layout.firstBaseline))
        }
    }
}

/**
 * The overlay as a layer over a 9:16 video or camera preview. [modifier] must give it the same bounds as the picture.
 *
 * With [hazeState] — set as `hazeSource` on the picture underneath — the parts of the style that blur the video (a blur
 * or glass panel, Metro's tiles, Aero) are drawn through Haze at the scene's own backdrop rects, so what blurs is
 * exactly what the export will blur. Without it they show only their tint.
 */
@Composable
fun ShotOverlay(
    shot: Shot,
    videoTimeUs: Long,
    shotStartUs: Long,
    settings: OverlaySettings,
    style: GraphStyle,
    title: String,
    fonts: OverlayFonts,
    modifier: Modifier = Modifier,
    recipe: String? = null,
    hazeState: HazeState? = null,
    backdropLuma: Float? = null,
    labels: OverlayLabels = rememberOverlayLabels(),
) =
    ShotOverlay(
        shot,
        { videoTimeUs },
        shotStartUs,
        settings,
        style,
        title,
        fonts,
        modifier,
        recipe,
        hazeState,
        { backdropLuma },
        labels,
    )

/**
 * The overlay at the moment [videoTimeUs] says, read only where it is drawn: a playing video or a running take changes
 * it every frame, and only the drawing has to follow, not the composition. [backdropLuma] likewise.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun ShotOverlay(
    shot: Shot,
    videoTimeUs: () -> Long,
    shotStartUs: Long,
    settings: OverlaySettings,
    style: GraphStyle,
    title: String,
    fonts: OverlayFonts,
    modifier: Modifier = Modifier,
    recipe: String? = null,
    hazeState: HazeState? = null,
    backdropLuma: () -> Float? = { null },
    labels: OverlayLabels = rememberOverlayLabels(),
) {
    val measurer = rememberTextMeasurer(cacheSize = OVERLAY_TEXT_CACHE)
    val fontFamily = fonts.of(style.text.font)
    val metrics = remember(measurer, fontFamily) { ComposeTextMetrics(measurer, fontFamily) }
    val time by rememberUpdatedState(videoTimeUs)
    val luma by rememberUpdatedState(backdropLuma)
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f) return@BoxWithConstraints
        fun scene() =
            buildOverlayScene(
                shot,
                time(),
                shotStartUs,
                settings,
                style,
                title,
                frameWidth = w,
                frameHeight = h,
                metrics = metrics,
                labels = labels,
                recipe = recipe,
                backdropLuma = luma(),
            )
        if (hazeState != null && style.blursVideo) {
            // The blurred patches are composables, so they live in composition; they hardly ever move as the video
            // plays (a panel, its tiles), and this only recomposes when they do.
            val backdrops by
                remember(shot, shotStartUs, settings, style, title, w, h, metrics, labels, recipe) {
                    derivedStateOf(structuralEqualityPolicy()) { scene().prims.filterIsInstance<BackdropPrim>() }
                }
            Backdrops(backdrops, hazeState)
        }
        Canvas(Modifier.fillMaxSize()) { drawScene(scene(), measurer, fontFamily) }
    }
}

/** The style's blurred patches over the video, through Haze, at the scene's own backdrop rects. */
@OptIn(ExperimentalHazeApi::class)
@Composable
private fun Backdrops(backdrops: List<BackdropPrim>, hazeState: HazeState) {
    val density = LocalDensity.current
    for (b in backdrops) {
        with(density) {
            val shape = RoundedCornerShape(b.radius.toDp())
            val input = HazeInput.Sources(hazeState)
            Box(
                Modifier.offset { IntOffset(b.box.left.roundToInt(), b.box.top.roundToInt()) }
                    .size(b.box.width.toDp(), b.box.height.toDp())
                    .clip(shape)
                    .then(
                        if (b.glass != null) {
                            Modifier.hazeGlass(
                                input,
                                b.glass!!.toHazeStyle(
                                    shape,
                                    b.box.width,
                                    b.box.height,
                                    b.tint,
                                    b.blur.toDp(),
                                    density,
                                ),
                            )
                        } else {
                            Modifier.hazeBlur(
                                input,
                                HazeBlurStyle {
                                    blurRadius(b.blur.toDp())
                                    noiseFactor(0f)
                                    colorEffects(emptyList())
                                    backgroundColor(Color.Transparent)
                                },
                            )
                        }
                    )
            )
        }
    }
}

/** The centre of the pixel [v] falls in, where a one-pixel line covers exactly one row or column. */
private fun snap(v: Float): Float = kotlin.math.floor(v) + 0.5f

/**
 * The Liquid Glass settings as Haze's [GlassStyle] for a panel [width] × [height] px, through [toHaze]: the values the
 * exporters' glass shader takes, given to Haze the same way, so the preview and the export are the same glass.
 */
@OptIn(ExperimentalHazeApi::class)
fun GlassSettings.toHazeStyle(
    shape: RoundedCornerShape,
    width: Float,
    height: Float,
    tint: Int,
    blur: Dp,
    density: Density,
): GlassStyle {
    val g = toHaze(width, height)
    return with(density) {
        GlassStyle {
            shape(shape)
            optics(
                refractionStrength = g.refractionStrength,
                refractionHeightFraction = g.refractionHeight,
                refractionDisplacement = g.refractionDisplacement.toDp(),
                blurRadius = blur,
                refractionFoldStrength = 0f,
                refractionDetailIntensity = 0f,
                refractionProfile = HazeRefractionProfile.Surface,
            )
            surfaceProfile(SurfaceProfile.Circle)
            specularIntensity(g.specularIntensity)
            specularExponent(g.specularExponent)
            fresnelExponent(g.fresnelExponent)
            edgeShadow(Color.Black.copy(alpha = g.edgeShadow))
            edgeSoftness(g.edgeSoftness.toDp())
            ambientResponse(g.ambient)
            lightPosition(BiasAlignment(g.lightX, g.lightY))
            chromaticAberrationStrength(g.dispersion)
            chromaticAberrationMode(ChromaticAberrationMode.Simple)
            contrast(g.contrast)
            whitePoint(g.whitePoint)
            chromaMultiplier(g.saturation)
            contentNormalBlend(g.normalBlend)
            tint(Color(tint))
        }
    }
}
