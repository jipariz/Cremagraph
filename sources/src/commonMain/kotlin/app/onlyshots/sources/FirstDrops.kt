package app.onlyshots.sources

import app.onlyshots.core.model.StopRule
import kotlin.math.abs

/**
 * Auto-start for the scale-only source, with its own zero instead of trusting the scale's tare.
 *
 * While waiting, the zero is the weight the scale has held for [settleUs] (within [settleG]), so a cup already on the
 * scale is part of the zero, not a shot. A scale that only reports changes has held its last reading the whole time it
 * was quiet. The shot starts when two readings in a row are more than [thresholdG] above the zero, at the first of
 * them; a single noisy reading is not drops.
 *
 * A jump of more than [stepG] between two readings is a cup or a pour. Before a shot it is a cup if the weight settles
 * (a new zero) and a pour if it is still climbing a second later (the shot starts at the jump, from the old zero).
 * During a shot only a drop counts: the cup came off or the scale was tared, which ends the shot and re-arms the
 * trigger; weight shooting up is just a fast pour.
 *
 * Weights come back relative to the zero (frozen while a shot runs), so the graph starts at 0 g, not at the cup.
 */
class FirstDropsDetector(
    private val thresholdG: Float = StopRule.FIRST_DROPS_G,
    private val stepG: Float = STEP_G,
    private val settleG: Float = SETTLE_G,
    private val settleUs: Long = SETTLE_US,
) {
    /** One reading as the shot sees it: its weight above the zero, and the start when this reading is it. */
    data class Result(val weight: Float, val started: BrewEvent.Started?)

    /** The weight the shot is measured from, once the scale has settled; null while it moves. */
    var zero: Float? = null
        private set

    private var last: Float? = null
    private val recent = ArrayDeque<Pair<Long, Float>>()

    /** Whether a shot is running: from the first drops until the cup comes off (or the scale is tared). */
    var started: Boolean = false
        private set

    fun onReading(tUs: Long, weight: Float): Result {
        val previous = last
        last = weight
        if (previous != null && abs(weight - previous) > stepG) {
            if (started) {
                // During a shot the weight only grows: a fast pour is part of it. A drop is the cup coming off or a
                // tare, which ends the shot and re-arms the trigger.
                if (weight < previous) startOver()
            } else {
                // A cup put down, or a pour that starts fast: a cup settles, a pour keeps climbing.
                pour = if (weight > previous) pour ?: zero?.let { Pour(tUs, it) } else null
                startOver()
            }
        }
        // Many scales only send a reading when the weight changes: a quiet scale has held its last weight all along,
        // so the zero is judged up to now, before this reading counts.
        if (!started && zero == null) zero = settledWeight(tUs)?.also { pour = null }
        recent.addLast(tUs to weight)
        // Keep the reading that was showing when the window starts: it held until the next one.
        while (recent.size > 1 && recent[1].first <= tUs - settleUs) recent.removeFirst()
        if (!started) {
            pour?.let { p ->
                p.seen.addLast(tUs to weight)
                if (tUs - p.atUs >= POUR_US) {
                    if (p.stillClimbing(tUs, weight)) {
                        // Still rising a second after the jump: a pour, measured from before it.
                        started = true
                        zero = p.zero
                        pour = null
                        return Result(weight - p.zero, BrewEvent.Started(p.atUs))
                    }
                    pour = null
                }
                return Result(weight - p.zero, null)
            }
            val z = zero ?: settledWeight(tUs)?.also { zero = it }
            if (z != null && weight - z > thresholdG) {
                // Two readings in a row over the zero: one noisy reading is not drops.
                above++
                if (above >= READINGS_ABOVE) {
                    started = true
                    startAt = startAt ?: tUs
                    return Result(weight - z, BrewEvent.Started(startAt!!))
                }
                if (above == 1) startAt = tUs
            } else {
                above = 0
                startAt = null
            }
        }
        return Result(zero?.let { weight - it } ?: 0f, null)
    }

    /** A jump up from a settled [zero] at [atUs], not yet known to be a cup (it settles) or a pour (it climbs on). */
    private class Pour(val atUs: Long, val zero: Float) {
        /** Its own readings: the jumps of a fast pour keep clearing the settling window. */
        val seen = ArrayDeque<Pair<Long, Float>>()

        /** Rising by more than a gram over the last half second, as a pour does and a cup that has landed does not. */
        fun stillClimbing(tUs: Long, weight: Float): Boolean {
            val before = seen.lastOrNull { it.first <= tUs - CLIMB_US } ?: seen.first()
            return weight - before.second > CLIMB_G
        }
    }

    private var pour: Pour? = null

    private fun startOver() {
        zero = null
        started = false
        above = 0
        startAt = null
        recent.clear()
    }

    private var above = 0
    private var startAt: Long? = null

    /**
     * Whether the last reading belongs to the shot: once it runs, and already for the first reading over the zero,
     * which the start (confirmed by the next one) is timed from.
     */
    val brewing: Boolean
        get() = started || above > 0 || pour != null

    /** The weight the scale has held for [settleUs] up to [tUs], within [settleG], or null while it still moves. */
    private fun settledWeight(tUs: Long): Float? {
        if (recent.isEmpty() || tUs - recent.first().first < settleUs) return null
        val min = recent.minOf { it.second }
        val max = recent.maxOf { it.second }
        if (max - min > settleG) return null
        return recent.sumOf { it.second.toDouble() }.toFloat() / recent.size
    }

    /** After taring from the app: the next settled weight is the zero again. */
    fun reset() {
        startOver()
        last = null
        pour = null
    }

    companion object {
        /** More than this between two readings is a cup or a tare; drops never land this fast. */
        const val STEP_G = 5f
        /** A cheap scale's display can wander a few tenths with nothing on it. */
        const val SETTLE_G = 0.4f
        const val SETTLE_US = 600_000L
        const val READINGS_ABOVE = 2

        /** How long a jump must keep climbing to be a pour, not a cup. */
        const val POUR_US = 1_000_000L
        const val CLIMB_US = 500_000L
        const val CLIMB_G = 1f
    }
}
