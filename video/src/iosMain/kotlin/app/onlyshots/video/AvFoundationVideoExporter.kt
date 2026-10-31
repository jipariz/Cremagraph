@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.onlyshots.video

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Reframe
import app.onlyshots.core.model.Size
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.overlayTitle
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.overlay.compose.OVERLAY_TEXT_CACHE
import app.onlyshots.overlay.compose.OverlayFonts
import app.onlyshots.overlay.compose.OverlayFrameRenderer
import app.onlyshots.overlay.compose.overlayLabels
import app.onlyshots.overlay.scene.BackdropPrim
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderStatusFailed
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferPoolCreatePixelBuffer
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.darwin.dispatch_queue_create

/**
 * AVFoundation export. Trims with the writer session (samples keep their source timestamps, so the overlay gets the
 * true video time), decodes BGRA frames, draws each one scaled and cropped to 9:16 plus the overlay bitmap from
 * [OverlayFrameRenderer] with Skia straight into the writer's pixel buffer, and encodes H.264. Audio is decoded to PCM
 * and re-encoded as AAC.
 *
 * The handover suggests an AVVideoCompositing compositor; the reader/writer loop does the same job without registering
 * an Objective-C compositor class from Kotlin.
 */
class AvFoundationVideoExporter(
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
    ): Flow<ExportProgress> = channelFlow {
        val overlayFonts = fonts()
        trySend(ExportProgress.Rendering(0f))
        val result =
            withContext(Dispatchers.Default) {
                runCatching { render(item, quality, withSound, out, alignmentBarcode, overlayFonts, this@channelFlow) }
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
        progress: ProducerScope<ExportProgress>,
    ) {
        val labels = overlayLabels()
        val size = quality.size(item.format)
        val url = NSURL.fileURLWithPath(videoPath(item).toString())
        val asset = AVURLAsset.URLAssetWithURL(url, null)
        val videoTrack =
            asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: error("no video track")
        val audioTrack =
            if (withSound && item.hasAudio) asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
            else null

        NSFileManager.defaultManager.removeItemAtPath(out.toString(), null)
        val start = CMTimeMake(item.trimInUs, 1_000_000)
        val end = CMTimeMake(item.trimOutUs, 1_000_000)

        val reader = memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            AVAssetReader(asset, err.ptr) ?: error("reader: ${err.value?.localizedDescription}")
        }
        reader.timeRange = CMTimeRangeMake(start, CMTimeMake(item.trimOutUs - item.trimInUs, 1_000_000))
        val videoOut =
            AVAssetReaderTrackOutput.assetReaderTrackOutputWithTrack(
                videoTrack,
                // kCVPixelBufferPixelFormatTypeKey
                mapOf<Any?, Any?>("PixelFormatType" to kCVPixelFormatType_32BGRA.toInt()),
            )
        videoOut.alwaysCopiesSampleData = false
        reader.addOutput(videoOut)
        val audioOut = audioTrack?.let {
            AVAssetReaderTrackOutput.assetReaderTrackOutputWithTrack(
                    it,
                    mapOf<Any?, Any?>(
                        AVFormatIDKey to kAudioFormatLinearPCM.toInt(),
                        AVSampleRateKey to 48_000,
                        AVNumberOfChannelsKey to 2,
                    ),
                )
                .also { o -> reader.addOutput(o) }
        }

        val writer = memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            AVAssetWriter.assetWriterWithURL(NSURL.fileURLWithPath(out.toString()), AVFileTypeMPEG4, err.ptr)
                ?: error("writer: ${err.value?.localizedDescription}")
        }
        val videoIn =
            AVAssetWriterInput.assetWriterInputWithMediaType(
                AVMediaTypeVideo,
                mapOf<Any?, Any?>(
                    AVVideoCodecKey to AVVideoCodecTypeH264,
                    AVVideoWidthKey to size.width,
                    AVVideoHeightKey to size.height,
                    AVVideoCompressionPropertiesKey to
                        mapOf<Any?, Any?>(AVVideoAverageBitRateKey to bitrate(quality, size)),
                ),
            )
        videoIn.expectsMediaDataInRealTime = false
        val adaptor =
            AVAssetWriterInputPixelBufferAdaptor.assetWriterInputPixelBufferAdaptorWithAssetWriterInput(
                videoIn,
                // kCVPixelBufferPixelFormatTypeKey, kCVPixelBufferWidthKey, kCVPixelBufferHeightKey
                mapOf<Any?, Any?>(
                    "PixelFormatType" to kCVPixelFormatType_32BGRA.toInt(),
                    "Width" to size.width,
                    "Height" to size.height,
                ),
            )
        writer.addInput(videoIn)
        val audioIn = audioOut?.let {
            AVAssetWriterInput.assetWriterInputWithMediaType(
                    AVMediaTypeAudio,
                    mapOf<Any?, Any?>(
                        AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
                        AVSampleRateKey to 48_000,
                        AVNumberOfChannelsKey to 2,
                        AVEncoderBitRateKey to 128_000,
                    ),
                )
                .also { i ->
                    i.expectsMediaDataInRealTime = false
                    writer.addInput(i)
                }
        }

        val shot = item.shot
        val shotStart = item.shotStartInVideoUs
        // A style that adapts to the video needs to know how bright it is behind the graph, frame by frame.
        val style = shot?.let { GraphStyles.resolve(item.overlay, it.source) }
        val luma =
            if (style?.adapt?.enabled == true) {
                LumaTrack.measure(
                    IosFrameSampler(),
                    videoPath(item),
                    item.overlay.rect,
                    item.trimInUs,
                    item.trimOutUs,
                    item.sourceMapping(AvFoundationVideoProbe().info(videoPath(item)).aspect),
                )
            } else null
        val renderer =
            if (shot != null && shotStart != null) {
                OverlayFrameRenderer(
                    shot = shot,
                    shotStartUs = shotStart,
                    settings = item.overlay,
                    style = style ?: GraphStyles.resolve(item.overlay, shot.source),
                    title = overlayTitle(shot, item.overlay, labels.scale),
                    labels = labels,
                    recipe = item.overlay.recipe,
                    frameWidth = size.width,
                    frameHeight = size.height,
                    textMeasurer =
                        TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, OVERLAY_TEXT_CACHE),
                    fonts = overlayFonts,
                    alignmentBarcode = alignmentBarcode,
                    fps = fps,
                    backdropLuma = { t -> luma?.at(t) },
                )
            } else null
        val overlayBitmap = renderer?.newBitmap()

        check(reader.startReading()) { "reader: ${reader.error?.localizedDescription}" }
        check(writer.startWriting()) { "writer: ${writer.error?.localizedDescription}" }
        writer.startSessionAtSourceTime(start)

        val rotation = videoTrack.rotationDegrees()
        val totalUs = (item.trimOutUs - item.trimInUs).coerceAtLeast(1)
        val drawer = FrameDrawer(size.width, size.height, rotation, item.reframe)

        suspend fun pump(input: AVAssetWriterInput, label: String, next: () -> Boolean) =
            suspendCancellableCoroutine { cont ->
                input.requestMediaDataWhenReadyOnQueue(dispatch_queue_create("onlyshots.export.$label", null)) {
                    while (input.readyForMoreMediaData) {
                        if (!next()) {
                            input.markAsFinished()
                            if (cont.isActive) cont.resume(Unit)
                            break
                        }
                    }
                }
            }

        val video = suspend {
            pump(videoIn, "video") {
                val sb: CMSampleBufferRef = videoOut.copyNextSampleBuffer() ?: return@pump false
                try {
                    val pts = CMSampleBufferGetPresentationTimeStamp(sb)
                    val videoUs = (CMTimeGetSeconds(pts) * 1_000_000).roundToInt().toLong()
                    val src = CMSampleBufferGetImageBuffer(sb) ?: return@pump true
                    val dst = newPixelBuffer(adaptor) ?: error("no pixel buffer")
                    try {
                        if (renderer != null && overlayBitmap != null) renderer.renderInto(overlayBitmap, videoUs)
                        drawer.draw(
                            src,
                            dst,
                            overlayBitmap?.let { Triple(it, renderer!!.left, renderer.top) },
                            renderer?.backdrops(videoUs).orEmpty(),
                        )
                        adaptor.appendPixelBuffer(dst, pts)
                    } finally {
                        CVPixelBufferRelease(dst)
                    }
                    progress.trySend(
                        ExportProgress.Rendering(((videoUs - item.trimInUs).toFloat() / totalUs).coerceIn(0f, 0.99f))
                    )
                } finally {
                    CFRelease(sb)
                }
                true
            }
        }
        val audio = suspend {
            if (audioIn != null && audioOut != null) {
                pump(audioIn, "audio") {
                    val sb = audioOut.copyNextSampleBuffer() ?: return@pump false
                    try {
                        audioIn.appendSampleBuffer(sb)
                    } finally {
                        CFRelease(sb)
                    }
                    true
                }
            }
        }
        coroutineScope {
            val a = async { audio() }
            video()
            a.await()
        }

        if (reader.status == AVAssetReaderStatusFailed) error("reader: ${reader.error?.localizedDescription}")
        writer.endSessionAtSourceTime(end)
        suspendCancellableCoroutine { cont -> writer.finishWritingWithCompletionHandler { cont.resume(Unit) } }
        if (writer.status != AVAssetWriterStatusCompleted) error("writer: ${writer.error?.localizedDescription}")
    }

    private fun newPixelBuffer(adaptor: AVAssetWriterInputPixelBufferAdaptor): CVPixelBufferRef? = memScoped {
        val pool = adaptor.pixelBufferPool ?: return null
        val out = alloc<CVPixelBufferRefVar>()
        CVPixelBufferPoolCreatePixelBuffer(null, pool, out.ptr)
        out.value
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

/** Draws one decoded frame into an encoder buffer with [SkiaFrameDrawer], both as Skia views of the pixel buffers. */
private class FrameDrawer(private val width: Int, private val height: Int, rotation: Int, reframe: Reframe) {
    private val skia = SkiaFrameDrawer(width, height, rotation, reframe)

    /**
     * `Data.makeWithoutCopy` takes an owner only to keep that object reachable while the Data lives. The pixels belong
     * to the locked CVPixelBuffer, which outlives every draw, so one shared placeholder is enough.
     */
    private val borrowed = Data.makeUninitialized(1)

    fun draw(
        src: CVPixelBufferRef,
        dst: CVPixelBufferRef,
        overlay: Triple<androidx.compose.ui.graphics.ImageBitmap, Int, Int>?,
        backdrops: List<BackdropPrim> = emptyList(),
    ) {
        CVPixelBufferLockBaseAddress(src, kCVPixelBufferLock_ReadOnly)
        CVPixelBufferLockBaseAddress(dst, 0uL)
        try {
            val sw = CVPixelBufferGetWidth(src).toInt()
            val sh = CVPixelBufferGetHeight(src).toInt()
            val srcRow = CVPixelBufferGetBytesPerRow(src).toInt()
            val dstRow = CVPixelBufferGetBytesPerRow(dst).toInt()
            val srcData = Data.makeWithoutCopy(CVPixelBufferGetBaseAddress(src)!!.rawValue, srcRow * sh, borrowed)
            val image = Image.makeRaster(ImageInfo(sw, sh, ColorType.BGRA_8888, ColorAlphaType.PREMUL), srcData, srcRow)
            val surface =
                Surface.makeRasterDirect(
                    ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
                    CVPixelBufferGetBaseAddress(dst)!!.rawValue,
                    dstRow,
                )
            skia.draw(image, surface, overlay, backdrops)
            surface.close()
            image.close()
            srcData.close()
        } finally {
            CVPixelBufferUnlockBaseAddress(dst, 0uL)
            CVPixelBufferUnlockBaseAddress(src, kCVPixelBufferLock_ReadOnly)
        }
    }
}
