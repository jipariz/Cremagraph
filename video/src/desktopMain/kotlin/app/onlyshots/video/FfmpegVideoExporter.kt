package app.onlyshots.video

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Size
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.overlayTitle
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.overlay.compose.OVERLAY_TEXT_CACHE
import app.onlyshots.overlay.compose.OverlayFonts
import app.onlyshots.overlay.compose.OverlayFrameRenderer
import app.onlyshots.overlay.compose.overlayLabels
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okio.Path
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

/**
 * The desktop editor's export: FFmpeg decodes the trimmed clip, [SkiaFrameDrawer] draws each frame with its blur, glass
 * and overlay exactly as the iOS exporter does, and FFmpeg encodes H.264 (+ AAC when [withSound] and the clip has
 * sound) into an MP4 that starts playing before it is fully downloaded.
 */
class FfmpegVideoExporter(
    private val videoPath: (VideoItem) -> Path,
    private val fonts: suspend () -> OverlayFonts,
) : VideoExporter {

    override fun export(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean,
    ): Flow<ExportProgress> = channelFlow {
        val overlayFonts = fonts()
        send(ExportProgress.Rendering(0f))
        val result =
            withContext(Dispatchers.Default) {
                runCatching {
                    render(item, quality, withSound, out, alignmentBarcode, overlayFonts) { f ->
                        trySend(ExportProgress.Rendering(f))
                    }
                }
            }
        send(
            result.fold(
                { ExportProgress.Done(out) },
                { ExportProgress.Failed(it.message ?: it::class.simpleName.orEmpty()) },
            )
        )
    }

    private suspend fun render(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean,
        overlayFonts: OverlayFonts,
        progress: (Float) -> Unit,
    ) {
        val labels = overlayLabels()
        val source = videoPath(item)
        val size = quality.size(item.format)
        out.toFile().delete()
        val grabber = openGrabber(source, audio = withSound && item.hasAudio)
        try {
            val sound = withSound && item.hasAudio && grabber.audioChannels > 0
            val fps = grabber.frameRate.takeIf { it > 0 } ?: 30.0
            val shot = item.shot
            val shotStart = item.shotStartInVideoUs
            val style = shot?.let { GraphStyles.resolve(item.overlay, it.source) }
            // A style that adapts to the video needs to know how bright it is behind the graph, frame by frame.
            val luma =
                if (style?.adapt?.enabled == true) {
                    LumaTrack.measure(
                        FfmpegFrameSampler(),
                        source,
                        item.overlay.rect,
                        item.trimInUs,
                        item.trimOutUs,
                        item.sourceMapping(grabber.displaySize.let { (w, h) -> w.toFloat() / h }),
                    )
                } else null
            val renderer =
                if (shot != null && shotStart != null && style != null) {
                    OverlayFrameRenderer(
                        shot = shot,
                        shotStartUs = shotStart,
                        settings = item.overlay,
                        style = style,
                        title = overlayTitle(shot, item.overlay, labels.scale),
                        labels = labels,
                        recipe = item.overlay.recipe,
                        frameWidth = size.width,
                        frameHeight = size.height,
                        textMeasurer =
                            TextMeasurer(
                                createFontFamilyResolver(),
                                Density(1f),
                                LayoutDirection.Ltr,
                                OVERLAY_TEXT_CACHE,
                            ),
                        fonts = overlayFonts,
                        alignmentBarcode = alignmentBarcode,
                        fps = fps.roundToInt(),
                        backdropLuma = { t -> luma?.at(t) },
                    )
                } else null
            val overlayBitmap = renderer?.newBitmap()
            val drawer = SkiaFrameDrawer(size.width, size.height, grabber.uprightRotation, item.reframe)
            val info = ImageInfo(size.width, size.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
            val surface = Surface.makeRaster(info)
            val pixels = Bitmap().apply { allocPixels(info) }
            val outFrame = Frame(size.width, size.height, Frame.DEPTH_UBYTE, 4)

            val recorder =
                FFmpegFrameRecorder(
                        out.toFile(),
                        size.width,
                        size.height,
                        if (sound) grabber.audioChannels.coerceAtMost(2) else 0,
                    )
                    .apply {
                        format = "mp4"
                        videoCodec = avcodec.AV_CODEC_ID_H264
                        pixelFormat = avutil.AV_PIX_FMT_YUV420P
                        frameRate = fps
                        videoBitrate = bitrate(quality, size)
                        setVideoOption("preset", "veryfast")
                        setOption("movflags", "+faststart")
                        if (sound) {
                            audioCodec = avcodec.AV_CODEC_ID_AAC
                            sampleRate = grabber.sampleRate
                            audioBitrate = 128_000
                        }
                        start()
                    }
            try {
                // To the keyframe before the trim; the frames before it are skipped below.
                grabber.setTimestamp(item.trimInUs, false)
                val totalUs = (item.trimOutUs - item.trimInUs).coerceAtLeast(1)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val frame = grabber.grabFrame(sound, true, true, false) ?: break
                    val videoUs = frame.timestamp
                    if (videoUs < item.trimInUs) continue
                    if (videoUs >= item.trimOutUs) {
                        if (frame.image != null) break
                        continue
                    }
                    if (frame.image != null) {
                        val picture = frame.toSkiaImage()
                        if (renderer != null && overlayBitmap != null) renderer.renderInto(overlayBitmap, videoUs)
                        drawer.draw(
                            picture,
                            surface,
                            overlayBitmap?.let { Triple(it, renderer!!.left, renderer.top) },
                            renderer?.backdrops(videoUs).orEmpty(),
                        )
                        picture.close()
                        surface.readPixels(pixels, 0, 0)
                        val buffer = outFrame.image[0] as ByteBuffer
                        buffer.clear()
                        buffer.put(pixels.readPixels() ?: error("no pixels"))
                        buffer.rewind()
                        recorder.timestamp = videoUs - item.trimInUs
                        recorder.record(outFrame, avutil.AV_PIX_FMT_BGRA)
                        progress(((videoUs - item.trimInUs).toFloat() / totalUs).coerceIn(0f, 1f))
                    } else if (sound && frame.samples != null) {
                        recorder.recordSamples(frame.sampleRate, frame.audioChannels, *frame.samples)
                    }
                }
                recorder.stop()
            } finally {
                recorder.release()
                surface.close()
                pixels.close()
                outFrame.close()
            }
        } finally {
            grabber.stop()
            grabber.release()
        }
    }

    /** The 9:16 bitrate for [q], scaled by how many pixels [size] has against a 9:16 frame of that class. */
    private fun bitrate(q: Quality, size: Size): Int {
        val base =
            when (q) {
                Quality.HD_1080 -> 16_000_000
                Quality.UHD_4K -> 45_000_000
            }
        val portrait = q.size(VideoFormat.PORTRAIT_9_16)
        return (base.toLong() * size.width * size.height / (portrait.width.toLong() * portrait.height)).toInt()
    }
}
