package app.onlyshots.overlay.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Shot
import app.onlyshots.overlay.AlignmentBarcode
import app.onlyshots.overlay.GraphStyle
import app.onlyshots.overlay.OverlayLabels
import app.onlyshots.overlay.scene.BackdropPrim
import app.onlyshots.overlay.scene.Box as SceneBox
import app.onlyshots.overlay.scene.buildOverlayScene
import kotlin.math.ceil
import kotlin.math.floor

/** Pixel bounds of the overlay inside a frame, for rendering only the overlay rect at export. */
fun overlayBounds(settings: OverlaySettings, frameWidth: Float, frameHeight: Float): SceneBox =
    SceneBox(
        settings.rect.x * frameWidth,
        settings.rect.y * frameHeight,
        settings.rect.right * frameWidth,
        settings.rect.bottom * frameHeight,
    )

/**
 * Renders export frames of one video: only the overlay rect, into a transparent bitmap the caller places at [left],
 * [top] of the output frame. Works outside composition (export runs in a background service / task) with a
 * [TextMeasurer] built from the platform font resolver.
 *
 * Not thread-safe; use one per export. [fps] is only used by [alignmentBarcode].
 */
class OverlayFrameRenderer(
    private val shot: Shot,
    private val shotStartUs: Long,
    private val settings: OverlaySettings,
    private val style: GraphStyle,
    private val title: String,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val textMeasurer: TextMeasurer,
    fonts: OverlayFonts,
    /** The overlay's words, resolved once before the export ([overlayLabels]). */
    private val labels: OverlayLabels,
    private val recipe: String? = null,
    private val alignmentBarcode: Boolean = false,
    private val fps: Int = 30,
    /** Brightness behind the graph at a video time, for styles that adapt to the video; null when unknown. */
    private val backdropLuma: (videoTimeUs: Long) -> Float? = { null },
) {
    /** Whether the style changes with the video behind it, so the exporter should measure that first. */
    val adaptsToVideo: Boolean = style.adapt.enabled

    private val bounds = overlayBounds(settings, frameWidth.toFloat(), frameHeight.toFloat())

    /** Whole pixels so the bitmap lands exactly where the preview draws the overlay. */
    val left: Int = floor(bounds.left).toInt()
    val top: Int = floor(bounds.top).toInt()
    val width: Int = (ceil(bounds.right).toInt() - left).coerceAtLeast(1)
    val height: Int = (ceil(bounds.bottom).toInt() - top).coerceAtLeast(1)

    private val fontFamily = fonts.of(style.text.font)
    private val metrics = ComposeTextMetrics(textMeasurer, fontFamily)
    private val drawScope = CanvasDrawScope()

    fun newBitmap(): ImageBitmap = ImageBitmap(width, height)

    /** Whether this style blurs the video anywhere; when not, the exporter skips the blur pass entirely. */
    val blursVideo: Boolean = style.blursVideo

    /**
     * The rects to blur in the frame at [videoTimeUs], in frame pixels: the same backdrops the preview hands to Haze.
     * The exporter blurs the decoded frame there before drawing the overlay over it.
     */
    fun backdrops(videoTimeUs: Long): List<BackdropPrim> =
        if (!blursVideo) emptyList()
        else
            buildOverlayScene(
                    shot,
                    videoTimeUs,
                    shotStartUs,
                    settings,
                    style,
                    title,
                    frameWidth.toFloat(),
                    frameHeight.toFloat(),
                    metrics,
                    labels,
                    recipe,
                    backdropLuma(videoTimeUs),
                )
                .prims
                .filterIsInstance<BackdropPrim>()

    /** Clears [target] (sized [width] × [height]) and draws the overlay for [videoTimeUs]. */
    fun renderInto(target: ImageBitmap, videoTimeUs: Long) {
        drawScope.draw(PixelDensity, LayoutDirection.Ltr, Canvas(target), Size(width.toFloat(), height.toFloat())) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            translate(-left.toFloat(), -top.toFloat()) {
                val scene =
                    buildOverlayScene(
                        shot,
                        videoTimeUs,
                        shotStartUs,
                        settings,
                        style,
                        title,
                        frameWidth.toFloat(),
                        frameHeight.toFloat(),
                        metrics,
                        labels,
                        recipe,
                        backdropLuma(videoTimeUs),
                    )
                drawScene(scene, textMeasurer, fontFamily)
                if (alignmentBarcode) drawBarcode(AlignmentBarcode.frameIndex(videoTimeUs, fps))
            }
        }
    }

    private fun DrawScope.drawBarcode(frame: Int) {
        val rowTop = AlignmentBarcode.overlayRowTop(settings.rect) * frameHeight
        val rowH = AlignmentBarcode.ROW_HEIGHT * frameHeight
        for (i in 0 until AlignmentBarcode.BITS) {
            val l = AlignmentBarcode.blockLeft(i) * frameWidth
            val r = AlignmentBarcode.blockLeft(i + 1) * frameWidth
            drawRect(
                color = if (AlignmentBarcode.bit(frame, i)) Color.White else Color.Black,
                topLeft = Offset(l, rowTop),
                size = Size(r - l, rowH),
            )
        }
    }
}
