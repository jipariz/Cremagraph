package app.onlyshots.video

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.overlay.AlignmentBarcode
import kotlin.time.Instant

/**
 * The spike test clip (`video/testdata/spike_alignment.mp4`): 45 s, 1080 × 1920, 30 fps, frame number as a barcode
 * along the top, 120 Hz tone from 3 s.
 */
object SpikeClip {
    const val NAME = "spike_alignment.mp4"
    const val DURATION_US = 45_000_000L

    fun item(path: String, trimInUs: Long, trimOutUs: Long) =
        VideoItem(
            id = "spike",
            file = path,
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = DURATION_US,
            origin = Origin.IMPORTED,
            hasAudio = true,
            shot = SampleShots.zer0,
            shotStartInVideoUs = 3_000_000,
            syncLocked = true,
            trimInUs = trimInUs,
            trimOutUs = trimOutUs,
            overlay = OverlaySettings(rect = AlignmentBarcode.SpikeRect),
        )
}
