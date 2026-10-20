package app.onlyshots.record

import app.onlyshots.camera.Recording
import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.VideoItem
import kotlin.time.Instant

/**
 * Turns a finished recording into its library entry.
 *
 * The camera file starts when the user armed, so it carries an idle head in front of the pre-roll: `trimInUs` cuts the
 * head and leaves exactly [preRollSeconds] before the take started (the shot, or the record button pressed by hand).
 * Graph time 0 is `shotStartInVideoUs`, which comes from the camera's trigger presentation time, never from a nominal
 * pre-roll — that is what keeps the overlay on the right frame. A take started by hand before the shot gets the shot's
 * start as many seconds after the trigger as the session saw it come.
 */
fun recordedVideoItem(
    id: String,
    file: String,
    createdAt: Instant,
    recording: Recording,
    finished: FinishedRecording,
    preRollSeconds: Int,
    overlay: OverlaySettings,
): VideoItem {
    // The camera's trigger is where the take was started, in the file as saved (joined files included).
    val takeStart = recording.triggerVideoUs
    val shotStart =
        finished.shotStartInVideoUs?.let { shot ->
            takeStart?.let { it + (shot - finished.takeStartInVideoUs) } ?: shot
        } ?: takeStart
    val trimIn = (takeStart ?: shotStart)?.let { (it - preRollSeconds * 1_000_000L).coerceAtLeast(0L) } ?: 0L
    return VideoItem(
        id = id,
        file = file,
        createdAt = createdAt,
        durationUs = recording.durationUs,
        origin = Origin.RECORDED,
        hasAudio = recording.hasAudio,
        shot = finished.shot,
        shotStartInVideoUs = shotStart,
        // In-app recordings are synced by construction: the flag cannot be moved.
        syncLocked = finished.shot != null && shotStart != null,
        trimInUs = trimIn,
        trimOutUs = recording.durationUs,
        overlay = overlay,
    )
}
