@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.onlyshots.camera

import app.onlyshots.core.model.StopRules
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterStatusUnknown
import platform.AVFoundation.AVAssetWriterStatusWriting
import platform.AVFoundation.AVCaptureAudioDataOutput
import platform.AVFoundation.AVCaptureAudioDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureExposureModeAutoExpose
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPreset3840x2160
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoMaxKeyFrameIntervalKey
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.exposureMode
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.exposurePointOfInterestSupported
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.focusPointOfInterestSupported
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.videoZoomFactor
import platform.AVFoundation.virtualDeviceSwitchOverVideoZoomFactors
import platform.CoreGraphics.CGPoint
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create

/**
 * AVCaptureSession + AVAssetWriter rear-camera recorder. Writing starts at [arm], so the pre-roll is already in the
 * file; [trigger] returns the presentation time of the shot start inside it.
 *
 * While armed and untriggered the writer is swapped every [HEAD_CAP_SECONDS] seconds on a sample boundary — no frames
 * are dropped, unlike the Android side. The old writer keeps writing until the new one holds the whole pre-roll, so a
 * shot that starts just after a swap is recorded into the old one, which has it.
 */
class IosCameraController(
    private val tempDir: String,
    private val headCapSeconds: Int = HEAD_CAP_SECONDS,
) : CameraController {

    val session = AVCaptureSession()

    private val _state = MutableStateFlow(CameraState())
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private val queue = dispatch_queue_create("app.onlyshots.camera", null)
    private val videoOut = AVCaptureVideoDataOutput()
    private val audioOut = AVCaptureAudioDataOutput()
    private var device: AVCaptureDevice? = null
    private var audioInputAdded = false

    private var writer: Writer? = null

    /** The writer before the last swap, still writing until the current one holds the whole pre-roll. */
    private var overlap: Writer? = null
    private var preRollUs = 0L

    /**
     * How many previews draw the frames themselves; while any do, every other camera frame is kept as a small bitmap in
     * [previewFrame]. A count, not a flag: during a screen change the leaving preview and the arriving one overlap.
     */
    var framesWanted = 0

    private val wantFrames: Boolean
        get() = framesWanted > 0

    /** The live picture as a Compose bitmap, for the preview a blur style needs (native layers cannot be blurred). */
    val previewFrame: StateFlow<androidx.compose.ui.graphics.ImageBitmap?>
        field = MutableStateFlow<androidx.compose.ui.graphics.ImageBitmap?>(null)

    private var tapCount = 0
    private var luma: FloatArray? = null

    private fun tap(sb: CMSampleBufferRef) {
        val buffer = platform.CoreMedia.CMSampleBufferGetImageBuffer(sb) ?: return
        tapCount++
        if (wantFrames && tapCount % 2 == 0) previewFrame.value = PixelBuffers.toImageBitmap(buffer, FRAME_WIDTH)
        if (tapCount % 15 == 0) luma = PixelBuffers.lumaGrid(buffer, LUMA_COLS, LUMA_ROWS)
    }

    override fun sampleLuma(x: Float, y: Float, w: Float, h: Float): Float? =
        luma?.meanIn(LUMA_COLS, LUMA_ROWS, x, y, w, h)

    /** [onSample] runs on the capture queue, [trigger] and [stop] on the main thread; both swap writers. */
    private val lock = NSLock()
    private var armed = false
    private var fourK = false

    /** Recording landscape (16:9): the file is landscape, turned as the screen is. */
    private var landscape = false
    private var triggerUs: Long? = null
    private var lastPtsUs = 0L
    private var fileCounter = 0

    private class Writer(val url: NSURL, val path: String) {
        var writer: AVAssetWriter? = null
        var video: AVAssetWriterInput? = null
        var audio: AVAssetWriterInput? = null
        var startUs: Long? = null
        var lastUs = 0L
        val closed = CompletableDeferred<Unit>()
    }

    private val delegate =
        object :
            NSObject(),
            AVCaptureVideoDataOutputSampleBufferDelegateProtocol,
            AVCaptureAudioDataOutputSampleBufferDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureOutput,
                didOutputSampleBuffer: CMSampleBufferRef?,
                fromConnection: AVCaptureConnection,
            ) {
                val sb = didOutputSampleBuffer ?: return
                onSample(sb, video = output === videoOut)
            }
        }

    init {
        videoOut.setSampleBufferDelegate(delegate, queue)
        audioOut.setSampleBufferDelegate(delegate, queue)
    }

    /** Opens the camera. Call once the camera permission is granted. */
    fun prepare() {
        if (session.inputs.isNotEmpty()) return
        session.beginConfiguration()
        session.sessionPreset = preset()
        _state.update {
            val uhd = AVCaptureSessionPreset3840x2160
            it.copy(supports4k = uhd != null && session.canSetSessionPreset(uhd))
        }
        val discovery =
            AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                deviceTypes =
                    listOf(
                        AVCaptureDeviceTypeBuiltInTripleCamera,
                        AVCaptureDeviceTypeBuiltInDualWideCamera,
                        AVCaptureDeviceTypeBuiltInWideAngleCamera,
                    ),
                mediaType = AVMediaTypeVideo,
                position = AVCaptureDevicePositionBack,
            )
        val camera = discovery.devices.firstOrNull() as? AVCaptureDevice
        if (camera == null) {
            _state.update { it.copy(error = CameraError.NoRearCamera) }
            session.commitConfiguration()
            return
        }
        device = camera
        AVCaptureDeviceInput.deviceInputWithDevice(camera, null)?.let {
            if (session.canAddInput(it)) session.addInput(it)
        }
        // BGRA, not the default YUV: the writer takes either, and BGRA can be drawn and measured directly for the
        // Compose preview a blur style needs and for "adapt to video".
        videoOut.videoSettings =
            mapOf<Any?, Any?>(
                platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey to platform.CoreVideo.kCVPixelFormatType_32BGRA
            )
        if (session.canAddOutput(videoOut)) session.addOutput(videoOut)
        applyRotation()
        session.commitConfiguration()
        session.startRunning()
        lensScale = if (hasUltraWide(camera)) 2.0 else 1.0
        val range =
            zoomRange(
                (camera.minAvailableVideoZoomFactor / lensScale).toFloat(),
                (camera.maxAvailableVideoZoomFactor / lensScale).toFloat(),
            )
        // A virtual camera switches lens at these factors: each one is where a longer lens takes over.
        val switches =
            camera.virtualDeviceSwitchOverVideoZoomFactors
                .mapNotNull { (it as? Number)?.toFloat() }
                .map { it / lensScale.toFloat() }
        val lenses = lensStops(listOf(range.start) + switches, range.start, range.endInclusive)
        _state.update {
            it.copy(
                previewReady = true,
                minZoom = range.start,
                maxZoom = range.endInclusive,
                lenses = lenses,
                error = null,
            )
        }
    }

    /**
     * AVFoundation counts zoom from the widest lens of a virtual camera, so with an ultra-wide its factor 1 is what the
     * camera app calls 0.5×. This is that ratio: device factor = zoom × [lensScale].
     */
    private var lensScale = 1.0

    /** A virtual camera reports its lens switch-overs; one above 1 means the base lens is an ultra-wide. */
    private fun hasUltraWide(camera: AVCaptureDevice): Boolean =
        camera.virtualDeviceSwitchOverVideoZoomFactors.any { (it as? Number)?.toFloat()?.let { f -> f > 1f } == true }

    override fun setZoom(zoom: Float) {
        val camera = device ?: return
        val s = _state.value
        val ratio = zoom.coerceIn(s.minZoom, s.maxZoom)
        if (camera.lockForConfiguration(null)) {
            camera.videoZoomFactor =
                (ratio * lensScale).coerceIn(camera.minAvailableVideoZoomFactor, camera.maxAvailableVideoZoomFactor)
            camera.unlockForConfiguration()
            _state.update { it.copy(zoom = ratio) }
        }
    }

    /** Maps a tap on the preview to the sensor; set by [CameraPreview]. */
    internal var focusPoint: ((x: Float, y: Float) -> CValue<CGPoint>)? = null

    override fun focusAt(x: Float, y: Float) {
        val camera = device ?: return
        if (!camera.lockForConfiguration(null)) return
        // Without the preview, landscape sensor coordinates: the portrait picture turned a quarter, a landscape one as
        // it is (or half a turn round).
        val point =
            focusPoint?.invoke(x, y)
                ?: when {
                    !landscape -> platform.CoreGraphics.CGPointMake(y.toDouble(), 1.0 - x)
                    interfaceRotationAngle() == 180.0 -> platform.CoreGraphics.CGPointMake(1.0 - x, 1.0 - y)
                    else -> platform.CoreGraphics.CGPointMake(x.toDouble(), y.toDouble())
                }
        if (camera.focusPointOfInterestSupported && camera.isFocusModeSupported(AVCaptureFocusModeAutoFocus)) {
            camera.focusPointOfInterest = point
            camera.focusMode = AVCaptureFocusModeAutoFocus
        }
        if (
            camera.exposurePointOfInterestSupported && camera.isExposureModeSupported(AVCaptureExposureModeAutoExpose)
        ) {
            camera.exposurePointOfInterest = point
            camera.exposureMode = AVCaptureExposureModeAutoExpose
        }
        camera.unlockForConfiguration()
        _state.update { it.copy(focusLocked = true) }
    }

    override fun unlockFocus() {
        val camera = device ?: return
        if (!camera.lockForConfiguration(null)) return
        if (camera.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus))
            camera.focusMode = AVCaptureFocusModeContinuousAutoFocus
        if (camera.isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
            camera.exposureMode = AVCaptureExposureModeContinuousAutoExposure
        }
        camera.unlockForConfiguration()
        _state.update { it.copy(focusLocked = false) }
    }

    override fun setMic(on: Boolean) {
        if (on == _state.value.micOn) return
        session.beginConfiguration()
        if (on) {
            if (!audioInputAdded) {
                AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)?.let { mic ->
                    AVCaptureDeviceInput.deviceInputWithDevice(mic, null)?.let {
                        if (session.canAddInput(it)) {
                            session.addInput(it)
                            audioInputAdded = true
                        }
                    }
                }
            }
            if (audioInputAdded && !session.outputs.contains(audioOut) && session.canAddOutput(audioOut))
                session.addOutput(audioOut)
        } else if (session.outputs.contains(audioOut)) {
            session.removeOutput(audioOut)
        }
        session.commitConfiguration()
        _state.update { it.copy(micOn = on && audioInputAdded) }
    }

    override fun setFourK(on: Boolean) {
        if (on == fourK || armed) return
        fourK = on
        if (session.inputs.isEmpty()) return
        session.beginConfiguration()
        session.sessionPreset = preset()
        session.commitConfiguration()
    }

    override fun setFormat(format: app.onlyshots.core.model.VideoFormat) {
        if (armed) return
        landscape = format.landscape
        applyRotation()
    }

    /**
     * Turned as the screen is, so the picture is upright whichever way the phone was turned: the record screen holds
     * the screen the format's way, and where it cannot (a foldable's inner display) does not arm until it has been
     * turned. A screen still the other way (mid-turn) films the format's default way: portrait upright, landscape as
     * the sensor.
     */
    private fun applyRotation() {
        val screen = interfaceRotationAngle()
        val screenLandscape = screen == 0.0 || screen == 180.0
        videoOut.connectionWithMediaType(AVMediaTypeVideo)?.videoRotationAngle =
            when {
                screenLandscape == landscape -> screen
                landscape -> 0.0
                else -> PORTRAIT_DEGREES
            }
    }

    private fun preset(): String {
        val uhd = AVCaptureSessionPreset3840x2160
        return if (fourK && uhd != null && session.canSetSessionPreset(uhd)) uhd else AVCaptureSessionPreset1920x1080!!
    }

    /** 4K eats about nine times the bytes per second, so its floor is nine times as far away. */
    private fun lowStorageBytes(): Long = if (fourK) StopRules.MIN_FREE_BYTES_4K else LOW_STORAGE_BYTES

    override suspend fun arm(preRollSeconds: Int) {
        if (armed) return
        if (freeBytes() < lowStorageBytes()) {
            _state.update { it.copy(error = CameraError.NoSpace) }
            return
        }
        prepare()
        preRollUs = preRollSeconds * 1_000_000L
        // The screen may have turned since the format was set.
        applyRotation()
        armed = true
        triggerUs = null
        _state.update { it.copy(error = null) }
        writer = newWriter()
    }

    override fun disarm() {
        lock.lock()
        armed = false
        val w = writer
        val o = overlap
        writer = null
        overlap = null
        triggerUs = null
        lock.unlock()
        w?.let { close(it, delete = true) }
        o?.let { close(it, delete = true) }
    }

    override fun trigger(): Long {
        lock.lock()
        try {
            var w = writer ?: return 0L
            val o = overlap
            overlap = null
            if (o?.startUs != null && lastPtsUs - (w.startUs ?: lastPtsUs) < preRollUs) {
                // Too soon after a swap for the new file to hold the pre-roll: the old one has it, and carries on.
                close(w, delete = true)
                writer = o
                w = o
            } else if (o != null) {
                close(o, delete = true)
            }
            val start = w.startUs ?: return 0L
            return (lastPtsUs - start).also { triggerUs = it }
        } finally {
            lock.unlock()
        }
    }

    override suspend fun stop(): Recording {
        lock.lock()
        val w = writer
        val o = overlap
        val trigger = triggerUs
        armed = false
        writer = null
        overlap = null
        triggerUs = null
        lock.unlock()
        o?.let { close(it, delete = true) }
        if (w == null) error("stop() without arm()")
        val durationUs = (w.lastUs - (w.startUs ?: w.lastUs)).coerceAtLeast(0L)
        close(w, delete = false)
        w.closed.await()
        return Recording(
            file = w.path,
            durationUs = durationUs,
            hasAudio = _state.value.micOn,
            triggerVideoUs = trigger,
        )
    }

    override fun freeBytes(): Long {
        val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(tempDir, null)
        return (attrs?.get(NSFileSystemFreeSize) as? Number)?.toLong() ?: Long.MAX_VALUE
    }

    private fun newWriter(): Writer {
        NSFileManager.defaultManager.createDirectoryAtPath(tempDir, true, null, null)
        val path = "$tempDir/rec-${fileCounter++}.mp4"
        val w = Writer(NSURL.fileURLWithPath(path), path)
        val writer = AVAssetWriter(w.url, AVFileTypeMPEG4, null) ?: return w
        val video =
            AVAssetWriterInput(
                    AVMediaTypeVideo,
                    mapOf<Any?, Any?>(
                        AVVideoCodecKey to AVVideoCodecTypeH264,
                        AVVideoWidthKey to shortSide().let { if (landscape) it * 16 / 9 else it },
                        AVVideoHeightKey to shortSide().let { if (landscape) it else it * 16 / 9 },
                        AVVideoCompressionPropertiesKey to
                            mapOf<Any?, Any?>(
                                AVVideoAverageBitRateKey to if (fourK) 45_000_000 else 16_000_000,
                                AVVideoMaxKeyFrameIntervalKey to 30,
                            ),
                    ),
                )
                .apply { expectsMediaDataInRealTime = true }
        val audio = AVAssetWriterInput(AVMediaTypeAudio, null).apply { expectsMediaDataInRealTime = true }
        // Only inputs the writer took are kept: finishing one it does not hold throws.
        if (writer.canAddInput(video)) writer.addInput(video).also { w.video = video }
        if (writer.canAddInput(audio)) writer.addInput(audio).also { w.audio = audio }
        writer.startWriting()
        w.writer = writer
        return w
    }

    private fun close(w: Writer, delete: Boolean) {
        val writer = w.writer
        // A writer that never started (or already failed) cannot be finished: AVFoundation throws an
        // NSException, which ends the app. There is nothing in its file worth keeping.
        if (writer != null && writer.status != AVAssetWriterStatusWriting) {
            if (writer.status == AVAssetWriterStatusUnknown) writer.cancelWriting()
            NSFileManager.defaultManager.removeItemAtPath(w.path, null)
            w.closed.complete(Unit)
            return
        }
        w.video?.markAsFinished()
        w.audio?.markAsFinished()
        w.startUs?.let { w.writer?.endSessionAtSourceTime(platform.CoreMedia.CMTimeMake(w.lastUs, 1_000_000)) }
        w.writer?.finishWritingWithCompletionHandler {
            if (delete) NSFileManager.defaultManager.removeItemAtPath(w.path, null)
            w.closed.complete(Unit)
        } ?: w.closed.complete(Unit)
    }

    private fun onSample(sb: CMSampleBufferRef, video: Boolean) {
        lock.lock()
        try {
            onSampleLocked(sb, video)
        } finally {
            lock.unlock()
        }
    }

    private fun onSampleLocked(sb: CMSampleBufferRef, video: Boolean) {
        val ptsUs = (CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sb)) * 1_000_000).toLong()
        if (video) {
            lastPtsUs = ptsUs
            tap(sb)
        }
        overlap?.let { o ->
            val input = if (video) o.video else o.audio
            o.lastUs = ptsUs
            if (input != null && input.readyForMoreMediaData) input.appendSampleBuffer(sb)
        }
        val w = writer ?: return
        if (w.startUs == null) {
            if (!video) return
            w.startUs = ptsUs
            w.writer?.startSessionAtSourceTime(CMSampleBufferGetPresentationTimeStamp(sb))
        }
        w.lastUs = ptsUs
        val input = if (video) w.video else w.audio
        if (input != null && input.readyForMoreMediaData) input.appendSampleBuffer(sb)
        if (video && triggerUs == null && armed && ptsUs - (w.startUs ?: ptsUs) > headCapSeconds * 1_000_000L) {
            // Head cap: swap writers on this frame boundary, so nothing is lost.
            val old = w
            val fresh = newWriter()
            writer = fresh
            fresh.startUs = ptsUs
            fresh.writer?.startSessionAtSourceTime(CMSampleBufferGetPresentationTimeStamp(sb))
            fresh.lastUs = ptsUs
            fresh.video?.takeIf { it.readyForMoreMediaData }?.appendSampleBuffer(sb)
            overlap?.let { close(it, delete = true) }
            overlap = old
        }
        // The new file now holds the whole pre-roll, so the old one is no longer needed.
        val o = overlap
        if (video && triggerUs == null && o != null && ptsUs - (w.startUs ?: ptsUs) >= preRollUs + OVERLAP_MARGIN_US) {
            overlap = null
            close(o, delete = true)
        }
    }

    private fun shortSide(): Int = if (fourK) 2160 else 1080

    private companion object {
        const val LOW_STORAGE_BYTES = 200L * 1024 * 1024
        const val PORTRAIT_DEGREES = 90.0
        const val FRAME_WIDTH = 540
        const val LUMA_COLS = 16
        const val LUMA_ROWS = 28

        /** Kept a little past the pre-roll, so a trigger on the boundary still finds it. */
        const val OVERLAP_MARGIN_US = 500_000L
    }
}

/**
 * The capture rotation that makes the picture upright for the way the screen is turned now: the back camera's sensor is
 * landscape-right, so a portrait screen is a quarter turn and landscape-left half a turn.
 */
internal fun interfaceRotationAngle(): Double {
    val scene =
        platform.UIKit.UIApplication.sharedApplication.connectedScenes.firstOrNull() as? platform.UIKit.UIWindowScene
    return when (scene?.interfaceOrientation) {
        platform.UIKit.UIInterfaceOrientationLandscapeRight -> 0.0
        platform.UIKit.UIInterfaceOrientationLandscapeLeft -> 180.0
        platform.UIKit.UIInterfaceOrientationPortraitUpsideDown -> 270.0
        else -> 90.0
    }
}
