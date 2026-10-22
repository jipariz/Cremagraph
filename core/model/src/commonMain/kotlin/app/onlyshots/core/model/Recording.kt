package app.onlyshots.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a recording ends on its own. All rules run on source (shot) time. */
@Serializable
sealed interface StopRule {
    /** Stop [tailSeconds] after the source reports the end of the shot. */
    @Serializable
    @SerialName("shot_end")
    data class ShotEndPlusTail(val tailSeconds: Int = 5) : StopRule {
        init {
            require(tailSeconds in TAIL_RANGE)
        }
    }

    /**
     * Stop once the weight changed by less than [SETTLE_DELTA_G] for [seconds] after the first drops, or at once when
     * the shot has ended (the cup lifted off the scale).
     */
    @Serializable
    @SerialName("weight_settles")
    data class WeightSettles(val seconds: Int = 3) : StopRule {
        init {
            require(seconds in SETTLE_RANGE)
        }
    }

    /** Stop [seconds] after the shot started. */
    @Serializable
    @SerialName("fixed")
    data class Fixed(val seconds: Int = 45) : StopRule {
        init {
            require(seconds in FIXED_RANGE)
        }
    }

    @Serializable @SerialName("manual") data object Manual : StopRule

    companion object {
        val TAIL_RANGE = 0..30
        val SETTLE_RANGE = 1..10
        val FIXED_RANGE = 15..180
        const val SETTLE_DELTA_G = 0.1f
        /** Weight above which the first drops have landed (also the scale's auto-start trigger). */
        const val FIRST_DROPS_G = 0.3f
    }
}

/**
 * Moves the rule's parameter by [delta], clamped to its own range, so a stepper can never build a rule that trips the
 * `require` in its constructor.
 */
fun StopRule.stepped(delta: Int): StopRule =
    when (this) {
        is StopRule.ShotEndPlusTail -> copy((tailSeconds + delta).coerceIn(StopRule.TAIL_RANGE))
        is StopRule.WeightSettles -> copy((seconds + delta).coerceIn(StopRule.SETTLE_RANGE))
        is StopRule.Fixed -> copy((seconds + delta).coerceIn(StopRule.FIXED_RANGE))
        StopRule.Manual -> this
    }

/** The rule's parameter as the stepper shows it, or null when the rule has none. */
val StopRule.stepLabel: String?
    get() =
        when (this) {
            is StopRule.ShotEndPlusTail -> "${tailSeconds}s"
            is StopRule.WeightSettles -> "${seconds}s"
            is StopRule.Fixed -> "${seconds}s"
            StopRule.Manual -> null
        }

/**
 * Whether the rule can end a recording from [kind] at all. A Bluetooth scale reports weight only, never the end of a
 * shot, so "shot end" would wait forever; "weight settles" is the scale's way to stop.
 */
fun StopRule.availableFor(kind: SourceKind): Boolean = !(this is StopRule.ShotEndPlusTail && kind == SourceKind.SCALE)

/** App defaults for new recordings; the editor never writes these. */
@Serializable
data class RecordingSettings(
    val autoStart: Boolean = true,
    val preRollSeconds: Int = 3,
    val stopRule: StopRule = StopRule.ShotEndPlusTail(),
    /**
     * The graph for the next recordings, its rect in a 9:16 frame. Each take films the camera's whole frame the way the
     * screen stands (9:16 or 16:9), and the editor crops it to the video's format.
     */
    val overlay: OverlaySettings = OverlaySettings(),
) {
    init {
        require(preRollSeconds in PRE_ROLL_RANGE)
    }

    /**
     * The settings a recording from [kind] really runs with: a rule the source cannot fire is swapped for the one that
     * does the same job there (a scale stops when the weight settles). The saved settings stay as they are, so a
     * machine still gets its "shot end".
     */
    fun effectiveFor(kind: SourceKind): RecordingSettings =
        if (stopRule.availableFor(kind)) this else copy(stopRule = StopRule.WeightSettles())

    companion object {
        val PRE_ROLL_RANGE = 0..10
    }
}

/** What the recorder knows about the shot at one moment. Times are shot-relative, in µs. */
data class ShotProgress(
    /** Current source time since shot start, or null when recording without a shot (manual start). */
    val nowUs: Long?,
    /** When the source reported the end of the shot, if it has. */
    val endedAtUs: Long? = null,
    /** Samples received so far, sorted by time. */
    val samples: List<Sample> = emptyList(),
)

enum class StopReason {
    RULE,
    MAX_DURATION,
    LOW_STORAGE,
}

object StopRules {
    const val MAX_RECORDING_US = 5L * 60 * 1_000_000
    const val MIN_FREE_BYTES = 200L * 1024 * 1024

    /** Five minutes of 4K is about 1.7 GB, so the 1080p floor would be reached far too late. */
    const val MIN_FREE_BYTES_4K = 1_500L * 1024 * 1024

    /**
     * Whether the recording should stop now. Safety stops (5 minutes, under [minFreeBytes]) apply to every rule
     * including manual. A recording started by hand has no shot clock; then only [StopRule.Fixed] (measured from the
     * recording start) and the safety stops apply.
     */
    fun evaluate(
        rule: StopRule,
        shot: ShotProgress,
        recordingElapsedUs: Long,
        freeBytes: Long,
        minFreeBytes: Long = MIN_FREE_BYTES,
    ): StopReason? {
        if (recordingElapsedUs >= MAX_RECORDING_US) return StopReason.MAX_DURATION
        if (freeBytes < minFreeBytes) return StopReason.LOW_STORAGE
        return if (ruleMet(rule, shot, recordingElapsedUs)) StopReason.RULE else null
    }

    private fun ruleMet(rule: StopRule, shot: ShotProgress, recordingElapsedUs: Long): Boolean {
        val now = shot.nowUs
        return when (rule) {
            StopRule.Manual -> false
            is StopRule.Fixed -> (now ?: recordingElapsedUs) >= rule.seconds * 1_000_000L
            is StopRule.ShotEndPlusTail -> {
                val ended = shot.endedAtUs
                now != null && ended != null && now >= ended + rule.tailSeconds * 1_000_000L
            }
            // An ended shot has nothing left to settle: the cup came off, or the scale was tared.
            is StopRule.WeightSettles ->
                now != null &&
                    (shot.endedAtUs?.let { now >= it } == true ||
                        weightSettled(shot.samples, now, rule.seconds * 1_000_000L))
        }
    }

    /**
     * True when, after the first drops, every weight in the trailing window stays within [StopRule.SETTLE_DELTA_G] and
     * the window is fully covered by samples taken after the first drops.
     */
    internal fun weightSettled(samples: List<Sample>, nowUs: Long, windowUs: Long): Boolean {
        val firstDrops = samples.firstOrNull { (it.weight ?: 0f) > StopRule.FIRST_DROPS_G } ?: return false
        val windowStart = nowUs - windowUs
        if (windowStart < firstDrops.tUs) return false
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var any = false
        for (s in samples) {
            if (s.tUs < windowStart || s.tUs > nowUs) continue
            val w = s.weight ?: continue
            any = true
            if (w < min) min = w
            if (w > max) max = w
        }
        return any && max - min < StopRule.SETTLE_DELTA_G
    }
}
