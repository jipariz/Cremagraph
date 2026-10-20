package app.onlyshots.sources

import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotProgress
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.TimeRange
import app.onlyshots.core.model.withDerivedWeightFlow
import kotlin.time.Instant

/**
 * Builds the shot of one recording from live source data. Not thread-safe: feed it from one coroutine. Source times go
 * in, shot times (µs from brew start) come out.
 *
 * Losing the connection does not stop anything: the stretch without data becomes a gap and the curves break there.
 */
class LiveShotAssembler(
    private val id: String,
    private val source: SourceKind,
    private val profileName: String,
    private val startedAt: Instant,
) {
    private val samples = ArrayList<Sample>()
    private val phaseStarts = ArrayList<Long>()
    private val gaps = ArrayList<TimeRange>()
    private var openGapFrom: Long? = null
    /**
     * Samples seen before the brew start. Events and samples arrive on separate flows, so a sample from just after the
     * start can be delivered before the start itself; these are replayed once the start is known.
     */
    private val beforeStart = ArrayDeque<Sample>()
    private var lastPhase: Int? = null

    /** Source time of brew start, once known. */
    var startSourceUs: Long? = null
        private set

    var endedAtUs: Long? = null
        private set

    var nowUs: Long? = null
        private set

    fun onBrewEvent(event: BrewEvent) {
        when (event) {
            is BrewEvent.Started ->
                if (startSourceUs == null) {
                    startSourceUs = event.atUs
                    // A scale's shot starts at the first drops, but the first reading counted is already over the
                    // trigger (a couple of grams for a fast pour): the shot begins from rest, so the graph and the
                    // readings start at 0 g. A reading at the very start then gives way to this one.
                    if (source == SourceKind.SCALE) {
                        samples += Sample(tUs = 0L, weight = 0f, weightFlow = 0f, brewing = true)
                        nowUs = 0L
                    }
                    val early = beforeStart.toList()
                    beforeStart.clear()
                    early.forEach(::onSample)
                }
            is BrewEvent.Ended ->
                startSourceUs?.let { start ->
                    val end = (event.atUs - start).coerceAtLeast(0L)
                    endedAtUs = end
                    // Samples travel on their own flow and can arrive before the end event.
                    while (samples.isNotEmpty() && samples.last().tUs > end) samples.removeAt(samples.lastIndex)
                }
        }
    }

    fun onSample(sourceSample: Sample) {
        val start = startSourceUs
        if (start == null) {
            beforeStart.addLast(sourceSample)
            while (sourceSample.tUs - beforeStart.first().tUs > PRE_START_WINDOW_US) beforeStart.removeFirst()
            return
        }
        val t = sourceSample.tUs - start
        if (t < 0) return
        // Idle readings (machine not brewing) never belong to the shot.
        if (!sourceSample.brewing) return
        // Idle readings after the brew ended do not belong to the shot.
        endedAtUs?.let { if (t > it) return }
        if (samples.isNotEmpty() && t <= samples.last().tUs) return
        openGapFrom?.let { from ->
            if (t > from) gaps += TimeRange(from, t)
            openGapFrom = null
        }
        val s = sourceSample.copy(tUs = t)
        if (s.phase != null && s.phase != lastPhase) {
            phaseStarts += if (samples.isEmpty()) 0L else t
            lastPhase = s.phase
        }
        samples += s
        nowUs = t
    }

    /** Advance the shot clock without data, e.g. from the recorder's tick while disconnected. */
    fun onTick(sourceNowUs: Long) {
        val start = startSourceUs ?: return
        val t = sourceNowUs - start
        if (t > (nowUs ?: -1L)) nowUs = t
    }

    fun onConnectionLost() {
        if (startSourceUs == null || openGapFrom != null) return
        openGapFrom = samples.lastOrNull()?.tUs ?: 0L
    }

    fun progress(): ShotProgress = ShotProgress(nowUs = nowUs, endedAtUs = endedAtUs, samples = samples.toList())

    fun shot(): Shot {
        val finished = samples.withDerivedWeightFlow()
        val allGaps = gaps.toMutableList()
        openGapFrom?.let { from -> nowUs?.let { if (it > from) allGaps += TimeRange(from, it) } }
        return Shot(
            id = id,
            source = source,
            profileName = profileName,
            startedAt = startedAt,
            durationUs = endedAtUs ?: finished.lastOrNull()?.tUs ?: 0L,
            finalWeight = finished.lastOrNull { it.weight != null }?.weight,
            samples = finished,
            phaseStartsUs = phaseStarts.toList(),
            gaps = allGaps,
        )
    }

    private companion object {
        const val PRE_START_WINDOW_US = 5_000_000L
    }
}
