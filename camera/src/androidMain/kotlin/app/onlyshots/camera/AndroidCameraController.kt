package app.onlyshots.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.StatFs
import android.os.SystemClock
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController.VIDEO_CAPTURE
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.onlyshots.core.model.StopRules
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import androidx.camera.video.Quality as CxQuality
import androidx.camera.video.Recording as CxRecording

/**
 * CameraX rear-camera recorder. Recording runs from [arm] into a file in [tempDir], so the
 * pre-roll is already on disk when the shot starts; [trigger] returns where in that file it did.
 *
 * While armed and untriggered the file is restarted every [HEAD_CAP_SECONDS] seconds so an app
 * left armed does not fill the disk. The file before is kept until the next restart: a shot that
 * starts sooner after a restart than the pre-roll gets the end of that file joined in front at
 * [stop] ([Mp4Join], no re-encoding), so the pre-roll is whole. CameraX drops ~200 ms between the
 * two files; that gap falls in the pre-roll, before the shot, so the graph's alignment holds.
 *
 * CameraX also ends a recording by itself whenever it rebinds the camera (the preview resized, for one). While armed,
 * a new file starts at once; a shot already running in the ended file is joined to the new one at [stop], so only the
 * moment of the rebind is missing, not the rest of the shot.
 */
class AndroidCameraController(
    private val context: Context,
    private val tempDir: File,
    private val headCapSeconds: Int = HEAD_CAP_SECONDS,
) : CameraController {

    // ponytail: recording while the device is held landscape writes a landscape-tagged file.
    // CameraController pins video rotation to the device orientation and exposes no override, and
    // LifecycleCameraController is final, so this cannot be turned off. The export crops to 9:16,
    // the same crop the 9:16 viewfinder shows, so framing holds — a landscape take just loses
    // resolution to the crop. Upgrade path: ProcessCameraProvider + VideoCapture, set the target
    // rotation directly.
    val cameraController = LifecycleCameraController(context).apply {
        setEnabledUseCases(VIDEO_CAPTURE)
        // The record screen does its own tap-to-focus and pinch-to-zoom, so it can show them and keep its zoom state.
        isTapToFocusEnabled = false
        isPinchToZoomEnabled = false
        videoCaptureQualitySelector = QualitySelector.from(
            CxQuality.FHD,
            FallbackStrategy.higherQualityOrLowerThan(CxQuality.FHD),
        )
    }

    private val _state = MutableStateFlow(CameraState())
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val executor: Executor = ContextCompat.getMainExecutor(context)

    private var recording: CxRecording? = null
    private var file: File? = null
    private var finalized: CompletableDeferred<Long>? = null
    private var rotateJob: Job? = null
    private var armed = false

    /** [arm] is waiting for the camera; see there. */
    private var arming = false
    private var fourK = false
    private var triggerVideoUs: Long? = null
    private var preRollUs = 0L

    /** The file before the current one and its finished duration, kept for a shot that starts just after a restart. */
    private var previous: File? = null
    private var previousDone: CompletableDeferred<Long>? = null

    /** The shot started in [previous]: CameraX ended that file mid-shot and the recording carried on in a new one. */
    private var triggerInPrevious = false

    /** Recorded duration from the last Status event, and when that event arrived. */
    private var statusUs = 0L
    private var statusAtNs = 0L

    /** CameraX can only record once it is bound to a lifecycle, which the preview does. */
    private val bound = CompletableDeferred<Unit>()

    fun bind(owner: LifecycleOwner) {
        cameraController.bindToLifecycle(owner)
        bound.complete(Unit)
        val zoom = cameraController.zoomState.value
        val camera = cameraController.cameraInfo
        _state.update {
            it.copy(
                previewReady = true,
                minZoom = zoom?.let { z -> zoomRange(z.minZoomRatio, z.maxZoomRatio).start } ?: it.minZoom,
                maxZoom = zoom?.let { z -> zoomRange(z.minZoomRatio, z.maxZoomRatio).endInclusive } ?: it.maxZoom,
                supports4k = camera != null &&
                    Recorder.getVideoCapabilities(camera).isQualitySupported(CxQuality.UHD, DynamicRange.SDR),
            )
        }
    }

    override fun setFourK(on: Boolean) {
        if (on == fourK || armed) return
        fourK = on
        val quality = if (on) CxQuality.UHD else CxQuality.FHD
        // Rebuilds the capture session, which is why this is refused while armed.
        cameraController.videoCaptureQualitySelector =
            QualitySelector.from(quality, FallbackStrategy.higherQualityOrLowerThan(quality))
    }

    override fun setZoom(zoom: Float) {
        val s = _state.value
        val ratio = zoom.coerceIn(s.minZoom, s.maxZoom)
        cameraController.setZoomRatio(ratio)
        _state.update { it.copy(zoom = ratio) }
    }

    /** The preview on screen, whose crop maps a tap to the sensor; set by [CameraPreview]. */
    internal var previewView: PreviewView? = null

    override fun sampleLuma(x: Float, y: Float, w: Float, h: Float): Float? {
        val bitmap = previewView?.bitmap ?: return null
        try {
            var sum = 0.0
            var n = 0
            for (gy in 0 until 3) for (gx in 0 until 4) {
                val px = ((x + w * (gx + 0.5f) / 4f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val py = ((y + h * (gy + 0.5f) / 3f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val c = bitmap.getPixel(px, py)
                fun lin(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
                sum += 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
                n++
            }
            return (sum / n).toFloat()
        } finally {
            bitmap.recycle()
        }
    }

    override fun focusAt(x: Float, y: Float) {
        val view = previewView
        val point =
            if (view != null && view.width > 0) view.meteringPointFactory.createPoint(x * view.width, y * view.height)
            else SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(x, y)
        cameraController.cameraControl?.startFocusAndMetering(
            // No auto-cancel: the user asked for this spot and keeps it until they clear it.
            FocusMeteringAction.Builder(point).disableAutoCancel().build(),
        )
        _state.update { it.copy(focusLocked = true) }
    }

    override fun unlockFocus() {
        cameraController.cameraControl?.cancelFocusAndMetering()
        _state.update { it.copy(focusLocked = false) }
    }

    override fun setMic(on: Boolean) {
        _state.update { it.copy(micOn = on && hasAudioPermission()) }
    }

    override suspend fun arm(preRollSeconds: Int) {
        // A second call while the first still waits for the camera would start a second recording, which CameraX
        // refuses with a crash: the first one arms, the others return.
        if (armed || arming) return
        if (freeBytes() < lowStorageBytes()) {
            _state.update { it.copy(error = CameraError.NoSpace) }
            return
        }
        _state.update { it.copy(error = null) }
        arming = true
        try {
            // Arming is asked for as soon as the permission is granted, which can be before the
            // preview has composed and bound the camera, and before CameraX has opened it.
            bound.await()
            cameraController.initializationFuture.await()
        } finally {
            arming = false
        }
        armed = true
        preRollUs = preRollSeconds * 1_000_000L
        startFile()
        rotateJob = scope.launch {
            while (isActive) {
                delay(headCapSeconds * 1000L)
                if (triggerVideoUs == null) restartFile()
            }
        }
    }

    override fun disarm() {
        armed = false
        rotateJob?.cancel()
        rotateJob = null
        recording?.stop()
        recording = null
        file?.delete()
        file = null
        finalized = null
        triggerVideoUs = null
        triggerInPrevious = false
        dropPrevious()
    }

    override fun trigger(): Long = positionUs().also { triggerVideoUs = it }

    override suspend fun stop(): Recording {
        val recorded = file ?: error("stop() without arm()")
        val done = finalized ?: error("stop() without arm()")
        val trigger = triggerVideoUs
        val triggerBefore = triggerInPrevious
        triggerInPrevious = false
        rotateJob?.cancel()
        rotateJob = null
        recording?.stop()
        recording = null
        armed = false
        triggerVideoUs = null
        val durationUs = done.await()
        file = null
        finalized = null
        val hasAudio = _state.value.micOn
        val before = previous
        val beforeDone = previousDone
        previous = null
        previousDone = null
        // The file before holds part of the shot: the pre-roll of a shot that started just after a restart, or the
        // start of a shot whose file CameraX ended.
        val needsBefore = trigger != null && (triggerBefore || trigger < preRollUs)
        if (needsBefore && before != null && beforeDone != null) {
            val joined = withContext(Dispatchers.IO) {
                val beforeUs = beforeDone.await()
                val from = if (triggerBefore) trigger - preRollUs else beforeUs - (preRollUs - trigger)
                val out = File(tempDir, "rec-${System.currentTimeMillis()}-joined.mp4")
                runCatching { Mp4Join.join(before, from, recorded, out) }
                    .onFailure { out.delete() }
                    .getOrNull()
                    ?.let { out to it }
            }
            before.delete()
            if (joined != null) {
                val (out, at) = joined
                recorded.delete()
                return Recording(
                    file = out.absolutePath,
                    durationUs = at.secondOffsetUs + durationUs,
                    hasAudio = hasAudio,
                    triggerVideoUs = if (triggerBefore) trigger - at.firstCutUs else at.secondOffsetUs + trigger,
                )
            }
        } else {
            before?.delete()
        }
        return Recording(
            file = recorded.absolutePath,
            durationUs = durationUs,
            hasAudio = hasAudio,
            triggerVideoUs = trigger,
        )
    }

    private fun dropPrevious() {
        val old = previous
        val oldDone = previousDone
        previous = null
        previousDone = null
        if (old != null) scope.launch {
            oldDone?.await()
            old.delete()
        }
    }

    /** 4K eats about nine times the bytes per second, so its floor is nine times as far away. */
    private fun lowStorageBytes(): Long = if (fourK) StopRules.MIN_FREE_BYTES_4K else LOW_STORAGE_BYTES

    override fun freeBytes(): Long = StatFs(tempDir.parentFile?.path ?: tempDir.path).availableBytes

    /** Position in the file being recorded, interpolated between Status events. */
    private fun positionUs(): Long =
        if (statusAtNs == 0L) 0L else statusUs + (SystemClock.elapsedRealtimeNanos() - statusAtNs) / 1_000

    @SuppressLint("MissingPermission")
    private fun startFile() {
        tempDir.mkdirs()
        val out = File(tempDir, "rec-${System.currentTimeMillis()}.mp4")
        val done = CompletableDeferred<Long>()
        file = out
        finalized = done
        statusUs = 0
        statusAtNs = 0
        recording = cameraController.startRecording(
            FileOutputOptions.Builder(out).build(),
            if (_state.value.micOn && hasAudioPermission()) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED,
            executor,
        ) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    statusUs = event.recordingStats.recordedDurationNanos / 1_000
                    statusAtNs = SystemClock.elapsedRealtimeNanos()
                }
                is VideoRecordEvent.Finalize -> {
                    done.complete(event.recordingStats.recordedDurationNanos / 1_000)
                    // Ended by CameraX, not by us: still armed and still the current file.
                    if (armed && file == out) onEndedByCamera()
                    else if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                        _state.update { it.copy(error = CameraError.RecordingFailed(event.cause?.message)) }
                    }
                }
            }
        }
    }

    /**
     * CameraX ended the current file (a rebind, a camera hiccup). Carry on in a new one, keeping this one: as the
     * pre-roll for a shot to come, or as the start of the shot already running.
     */
    private fun onEndedByCamera() {
        recording = null
        if (triggerVideoUs != null) {
            // Only one break can be mended; on a second, the part before the first is given up.
            if (triggerInPrevious) {
                triggerVideoUs = null
                triggerInPrevious = false
            } else {
                triggerInPrevious = true
            }
        }
        dropPrevious()
        previous = file
        previousDone = finalized
        startFile()
    }

    private fun restartFile() {
        recording?.stop()
        recording = null
        // The one before the file just stopped goes; the file just stopped stays for a shot that comes soon.
        dropPrevious()
        previous = file
        previousDone = finalized
        startFile()
    }

    private suspend fun com.google.common.util.concurrent.ListenableFuture<Void>.await() =
        suspendCancellableCoroutine { cont ->
            addListener({ cont.resume(Unit) }, executor)
            cont.invokeOnCancellation { cancel(false) }
        }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /** The stop rule uses the same threshold; refuse to arm below it. */
        const val LOW_STORAGE_BYTES = 200L * 1024 * 1024
    }
}
