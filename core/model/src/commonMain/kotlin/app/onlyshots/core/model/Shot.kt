package app.onlyshots.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** One reading from a source. [tUs] is microseconds from shot start. */
@Serializable
data class Sample(
    val tUs: Long,
    val pressure: Float? = null,
    val flow: Float? = null,
    val weight: Float? = null,
    val weightFlow: Float? = null,
    val temperature: Float? = null,
    val targetPressure: Float? = null,
    val targetFlow: Float? = null,
    val targetTemperature: Float? = null,
    val targetWeight: Float? = null,
    val phase: Int? = null,
    val brewing: Boolean = true,
)

/** Half-open time range `[startUs, endUs)` in shot time. */
@Serializable
data class TimeRange(val startUs: Long, val endUs: Long) {
    init {
        require(endUs >= startUs) { "endUs < startUs" }
    }

    operator fun contains(tUs: Long): Boolean = tUs >= startUs && tUs < endUs
}

@Serializable
data class Shot(
    val id: String,
    val source: SourceKind,
    val profileName: String,
    val startedAt: Instant,
    val durationUs: Long,
    val finalWeight: Float?,
    /** Sorted by [Sample.tUs]. */
    val samples: List<Sample>,
    /** Start of every phase, including the first one at 0 when the source reports phases. */
    val phaseStartsUs: List<Long>,
    /** Stretches with no data, e.g. the connection dropped while recording. Curves break here. */
    val gaps: List<TimeRange> = emptyList(),
)

/** A row in the "pick a shot" list, before the full shot is loaded. */
@Serializable
data class ShotSummary(
    val id: String,
    val source: SourceKind,
    val profileName: String,
    val startedAt: Instant,
    val durationUs: Long,
    val finalWeight: Float?,
)

fun Shot.summary(): ShotSummary = ShotSummary(id, source, profileName, startedAt, durationUs, finalWeight)
