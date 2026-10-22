package app.onlyshots.video

import app.onlyshots.overlay.compose.OverlayFonts
import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation
import app.onlyshots.core.model.Reframe
import app.onlyshots.core.model.Size
import app.onlyshots.core.model.overlayTitle
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.VideoItem
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.overlay.compose.OVERLAY_TEXT_CACHE
import app.onlyshots.overlay.compose.OverlayFrameRenderer
import app.onlyshots.overlay.compose.overlayLabels
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.Path
import java.io.File

/**
 * Media3 Transformer export: clip to the trim, scale and crop to 9:16 at [Quality], then an
 * [OverlayEffect] whose bitmap is drawn per frame by [OverlayFrameRenderer], the same code as
 * the preview. H.264 + AAC in MP4, hardware codecs where the device has them.
 *
 * Only the overlay rect is rendered per frame; Media3 places it at the rect's position.
 */
@UnstableApi
class Media3VideoExporter(
    private val context: Context,
    /** Resolves the source file of a video (library files, or the spike's test clip). */
    private val videoPath: (VideoItem) -> Path,
    private val fonts: suspend () -> OverlayFonts,
    private val fps: Int = 30,
) : VideoExporter {

    override fun export(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean,
    ): Flow<ExportProgress> = callbackFlow {
        val overlayFonts = fonts()
        val labels = overlayLabels()
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(item.trimInUs / 1000)
            .setEndPositionMs(item.trimOutUs / 1000)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(videoPath(item).toString())))
            .setClippingConfiguration(clipping)
            .build()

        // A style that adapts to the video needs to know how bright it is behind the graph, frame by frame.
        val style = item.shot?.let { GraphStyles.resolve(item.overlay, it.source) }
        // The picture's shape as shown: how much of it the crop to the video's format keeps.
        val sourceAspect = runCatching { AndroidVideoProbe().info(videoPath(item)).aspect }.getOrNull()
        val luma =
            if (style?.adapt?.enabled == true) {
                LumaTrack.measure(
                    AndroidFrameSampler(),
                    videoPath(item),
                    item.overlay.rect,
                    item.trimInUs,
                    item.trimOutUs,
                    item.sourceMapping(sourceAspect),
                )
            } else null
        val size = quality.size(item.format)
        val videoEffects = buildList {
            // The reframe first, on the whole picture, so it can bring in what the crop would cut off; then the crop.
            if (!item.reframe.isIdentity) add(reframeTransformation(item.reframe, item.format.ratio, sourceAspect))
            add(Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            val shot = item.shot
            val shotStart = item.shotStartInVideoUs
            if (shot != null && shotStart != null) {
                val measurer = TextMeasurer(
                    defaultFontFamilyResolver = createFontFamilyResolver(context),
                    defaultDensity = Density(1f),
                    defaultLayoutDirection = LayoutDirection.Ltr,
                    cacheSize = OVERLAY_TEXT_CACHE,
                )
                val renderer = OverlayFrameRenderer(
                    shot = shot,
                    shotStartUs = shotStart,
                    settings = item.overlay,
                    style = style ?: GraphStyles.resolve(item.overlay, shot.source),
                    title = overlayTitle(shot, item.overlay, labels.scale),
                    labels = labels,
                    recipe = item.overlay.recipe,
                    frameWidth = size.width,
                    frameHeight = size.height,
                    textMeasurer = measurer,
                    fonts = overlayFonts,
                    alignmentBarcode = alignmentBarcode,
                    fps = fps,
                    backdropLuma = { t -> luma?.at(t) },
                )
                // The video behind a blur or glass style is blurred first, then the graph drawn over it.
                if (renderer.blursVideo) add(BackdropBlurEffect { t -> renderer.backdrops(item.trimInUs + t) })
                add(OverlayEffect(ImmutableList.of<TextureOverlay>(GraphBitmapOverlay(renderer, item.trimInUs, size))))
            }
        }

        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(!(withSound && item.hasAudio))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    trySend(ExportProgress.Rendering(1f))
                    trySend(ExportProgress.Done(out))
                    close()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    trySend(ExportProgress.Failed(exportException.errorCodeName + ": " + (exportException.message ?: "")))
                    close()
                }
            })
            .build()

        File(out.toString()).parentFile?.mkdirs()
        transformer.start(edited, out.toString())
        trySend(ExportProgress.Rendering(0f))
        val poll = launch {
            val holder = ProgressHolder()
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(ExportProgress.Rendering(holder.progress / 100f))
                }
                delay(200)
            }
        }
        awaitClose {
            poll.cancel()
            transformer.cancel()
        }
    }.flowOn(Dispatchers.Main) // Transformer must be driven from a Looper thread.

}

/**
 * Hands Media3 a freshly drawn bitmap for every frame. Two bitmaps alternate so the overlay
 * texture is re-uploaded each frame (BitmapOverlay re-uploads when the instance changes).
 */
@UnstableApi
private class GraphBitmapOverlay(
    private val renderer: OverlayFrameRenderer,
    private val trimInUs: Long,
    size: Size,
) : BitmapOverlay() {
    private val buffers: Array<ImageBitmap> = arrayOf(renderer.newBitmap(), renderer.newBitmap())
    private var next = 0

    // Media3 anchors are in normalized device coordinates: x, y in -1..1, y up.
    private val settings: OverlaySettings = StaticOverlaySettings.Builder()
        .setOverlayFrameAnchor(-1f, 1f)
        .setBackgroundFrameAnchor(
            renderer.left / size.width.toFloat() * 2f - 1f,
            1f - renderer.top / size.height.toFloat() * 2f,
        )
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val target = buffers[next]
        next = 1 - next
        // Effects see times from the start of the clipped item; the overlay works in video time.
        renderer.renderInto(target, videoTimeUs = trimInUs + presentationTimeUs)
        return target.asAndroidBitmap()
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}

/**
 * [reframe] as Media3 wants it: a matrix on normalised device coordinates (-1..1, y up), applied to the whole picture
 * before it is cropped, centred, to the video's shape ([outputAspect]). The crop keeps [kx] of the picture's width and
 * [ky] of its height, so a move measured in frames is that much smaller in the picture: x' = z·x + z·kx(1 − 2cx),
 * y' = z·y + z·ky(2cy − 1). Where the picture already has the video's shape, both are 1 (Reframe.map in NDC).
 */
@UnstableApi
internal fun reframeTransformation(reframe: Reframe, outputAspect: Float, sourceAspect: Float?): MatrixTransformation {
    val z = reframe.zoom
    val kx = sourceAspect?.let { minOf(1f, outputAspect / it) } ?: 1f
    val ky = sourceAspect?.let { minOf(1f, it / outputAspect) } ?: 1f
    val matrix =
        Matrix().apply {
            setScale(z, z)
            postTranslate(z * kx * (1f - 2f * reframe.x), z * ky * (2f * reframe.y - 1f))
        }
    return MatrixTransformation { matrix }
}
