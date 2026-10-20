package app.onlyshots.camera

import kotlinx.coroutines.flow.StateFlow

data class CameraState(
    val previewReady: Boolean = false,
    /** Zoom as the camera app counts it: 1 is the main lens, 0.5 the ultra-wide where there is one. */
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val focusLocked: Boolean = false,
    val micOn: Boolean = false,
    val supports4k: Boolean = false,
    val error: CameraError? = null,
)

/** What went wrong with the camera; the record screen words it. [detail]: the platform's own message, if any. */
sealed interface CameraError {
    data object NoRearCamera : CameraError

    data object NoSpace : CameraError

    data class RecordingFailed(val detail: String?) : CameraError
}

/** What the recorder gets back when a recording file is closed. */
data class Recording(
    val file: String,
    val durationUs: Long,
    val hasAudio: Boolean,
    /** Presentation time of the trigger relative to the start of the file (pre-roll included). */
    val triggerVideoUs: Long?,
)

const val HEAD_CAP_SECONDS = 15

/** Past this the picture is digital zoom of a phone sensor, too soft to film a cup with. */
const val MAX_ZOOM = 10f

/** The zoom range to offer for what the lenses report: their minimum, and their maximum up to [MAX_ZOOM]. */
fun zoomRange(minRatio: Float, maxRatio: Float): ClosedFloatingPointRange<Float> {
    val min = minRatio.coerceAtLeast(0.1f)
    return min..maxRatio.coerceIn(min, maxOf(min, MAX_ZOOM))
}

/**
 * Rear camera, 9:16 (16:9 when recording landscape), 1080p at 30 fps (4K when supported). CameraX `VideoCapture` on
 * Android, AVCaptureSession + AVAssetWriter on iOS.
 *
 * Pre-roll: the file starts recording when the user arms, so the seconds before the trigger are already in it.
 * [trigger] reports where the shot starts in that file; the idle head in front of the pre-roll is cut by
 * `VideoItem.trimInUs`, which both exporters honour.
 *
 * ponytail: an idle head of up to [HEAD_CAP_SECONDS] seconds stays in the file (≈19 MB at 1080p30). A passthrough
 * head-trim at stop (`Transformer.setTransmuxVideo` / `AVAssetExportPresetPassthrough`) removes it; worth doing when 4K
 * is turned on.
 */
interface CameraController {
    val state: StateFlow<CameraState>

    /**
     * Relative luminance (0..1, linear light) of the live picture inside the normalized rect [x], [y], [w], [h]: what
     * the graph sits on, for styles that adapt to the video. Null when the platform cannot read the preview.
     */
    fun sampleLuma(x: Float, y: Float, w: Float, h: Float): Float? = null

    /** Any zoom in [CameraState.minZoom]..[CameraState.maxZoom]; out-of-range values are clamped. */
    fun setZoom(zoom: Float)

    /**
     * Tap to focus: x/y normalized in the preview (9:16, or 16:9 when recording landscape); locks focus and exposure.
     */
    fun focusAt(x: Float, y: Float)

    fun unlockFocus()

    fun setMic(on: Boolean)

    /** 4K instead of 1080p, where the camera has it. Ignored while armed or recording. */
    fun setFourK(on: Boolean)

    /**
     * The shape of the next recording: a landscape [format] (16:9) records landscape, as the phone is held; the others
     * record portrait 9:16 and are cropped later. Ignored while armed or recording. Where the camera follows the
     * screen's rotation by itself (Android), there is nothing to do: the record screen turns the screen.
     */
    fun setFormat(format: app.onlyshots.core.model.VideoFormat) {}

    /** Start buffering [preRollSeconds] of encoded video. Keep the screen awake while armed. */
    suspend fun arm(preRollSeconds: Int)

    fun disarm()

    /**
     * Marks the shot start in the file being recorded. Returns the camera presentation time of the trigger relative to
     * the file start, used for [app.onlyshots.core.model.ClockSync].
     */
    fun trigger(): Long

    /** Close the file. Also called on background, screen lock and safety stops. */
    suspend fun stop(): Recording

    fun freeBytes(): Long
}
