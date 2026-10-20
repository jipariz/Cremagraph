package app.onlyshots.storage

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.sample.SampleShots
import kotlin.time.Instant

internal fun videoItem(id: String, createdAtS: Long, linked: Boolean = true) =
    VideoItem(
        id = id,
        file = "$id.mp4",
        createdAt = Instant.fromEpochSeconds(createdAtS),
        durationUs = 41_000_000,
        origin = if (linked) Origin.RECORDED else Origin.IMPORTED,
        hasAudio = false,
        shot = if (linked) SampleShots.zer0 else null,
        shotStartInVideoUs = if (linked) 3_000_000 else null,
        syncLocked = linked,
        trimInUs = 0,
        trimOutUs = 41_000_000,
        overlay = OverlaySettings(),
    )
