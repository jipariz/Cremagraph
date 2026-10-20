package app.onlyshots.video

import app.onlyshots.core.model.ShotSummary
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Auto-pick for an imported video: a shot whose start falls inside the video's time window. Phones stamp either the
 * start or the end of a clip as its creation time, so the window is [createdAt − duration, createdAt + duration]. The
 * closest start to [createdAt] wins.
 */
fun pickShotForImport(createdAt: Instant, durationUs: Long, shots: List<ShotSummary>): ShotSummary? {
    val created = createdAt.toEpochMilliseconds() * 1000
    return shots
        .map { it to (it.startedAt.toEpochMilliseconds() * 1000 - created) }
        .filter { (_, d) -> abs(d) <= durationUs }
        .minByOrNull { (_, d) -> abs(d) }
        ?.first
}
