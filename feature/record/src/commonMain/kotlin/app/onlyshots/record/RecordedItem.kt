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
 * head and leaves exactly [preRollSeconds] before the shot start. Graph time 0 is `shotStartInVideoUs`, which comes
 * from the camera's trigger presentation time, never from a nominal pre-roll — that is what keeps the overlay on the
 * right frame.
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
    val shotStart = recording.triggerVideoUs ?: finished.shotStartInVideoUs
    val trimIn = shotStart?.let { (it - preRollSeconds * 1_000_000L).coerceAtLeast(0L) } ?: 0L
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
