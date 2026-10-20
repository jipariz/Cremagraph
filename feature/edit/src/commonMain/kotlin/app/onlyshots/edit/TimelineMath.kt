package app.onlyshots.edit

import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_cut_both
import app.onlyshots.core.resources.edit_cut_head
import app.onlyshots.core.resources.edit_cut_tail
import kotlin.math.abs
import org.jetbrains.compose.resources.StringResource

/** What a press on the timeline grabbed. */
enum class TimelineHandle {
    TRIM_IN,
    TRIM_OUT,
    SHOT_FLAG,
    PLAYHEAD,
}

/**
 * The timeline's horizontal geometry, the web editor's phone timeline. Fitted ([pxPerSecond] null), the whole clip
 * spans the strip between the [padPx] margins and the playhead moves along it. Zoomed, the playhead stays in the middle
 * and the clip slides under it.
 */
data class TimelineView(
    val widthPx: Float,
    val durationUs: Long,
    val playheadUs: Long,
    val padPx: Float,
    val pxPerSecond: Float? = null,
) {
    val zoomed: Boolean
        get() = pxPerSecond != null

    /** Pixels per second actually drawn: the zoom, or whatever fits the clip between the pads. */
    val scale: Float
        get() = pxPerSecond ?: fitScale

    val fitScale: Float
        get() = if (durationUs <= 0) 0f else (widthPx - 2 * padPx) / (durationUs / 1e6f)

    fun x(us: Long): Float =
        if (zoomed) widthPx / 2f + (us - playheadUs) / 1e6f * scale else padPx + us / 1e6f * fitScale

    fun us(x: Float): Long {
        val s = scale
        if (s <= 0f) return 0L
        val t = if (zoomed) playheadUs + ((x - widthPx / 2f) / s * 1e6f).toLong() else ((x - padPx) / s * 1e6f).toLong()
        return t.coerceIn(0L, durationUs)
    }

    /** Microseconds a drag of [dx] pixels moves, for sliding the clip under a fixed playhead. */
    fun usFor(dx: Float): Long = if (scale <= 0f) 0L else (dx / scale * 1e6f).toLong()
}

/** Pure timeline maths, so the drag rules can be tested without a screen. */
object TimelineMath {
    /** The longest zoom: 120 px of strip per second of clip. */
    const val MAX_PX_PER_SECOND = 120f

    /** Ruler steps in seconds; the first that leaves [minPx] between ticks is used. */
    private val rulerSteps = listOf(0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f)

    /**
     * What a press at [xPx] grabs: the nearest trim handle, or the shot flag when there is one to move, within
     * [grabPx]. Anything else scrubs the playhead.
     */
    fun hitHandle(xPx: Float, item: VideoItem, view: TimelineView, grabPx: Float): TimelineHandle {
        val candidates = buildList {
            add(TimelineHandle.TRIM_IN to item.trimInUs)
            add(TimelineHandle.TRIM_OUT to item.trimOutUs)
            if (item.shot != null && !item.syncLocked)
                item.shotStartInVideoUs?.let { add(TimelineHandle.SHOT_FLAG to it) }
        }
        return candidates
            .map { (handle, us) -> handle to abs(view.x(us) - xPx) }
            .filter { (_, d) -> d <= grabPx }
            .minByOrNull { (_, d) -> d }
            ?.first ?: TimelineHandle.PLAYHEAD
    }

    /**
     * What the trim does to the graph, when it cuts into the shot: its start, its end or both. Null otherwise. Only a
     * handle that was moved cuts: a video that itself ends with the shot (a scale's take stops as the weight settles,
     * the camera's file a moment short of the data) is not cut. Nor is less than [SHOT_CUT_TOLERANCE_US].
     */
    fun shotCut(item: VideoItem): StringResource? {
        val start = item.shotStartInVideoUs ?: return null
        val end = item.shotEndInVideoUs ?: return null
        val head = item.trimInUs > 0 && item.trimInUs > start + SHOT_CUT_TOLERANCE_US && item.trimInUs < end
        val tail =
            item.trimOutUs < item.durationUs && item.trimOutUs < end - SHOT_CUT_TOLERANCE_US && item.trimOutUs > start
        return when {
            head && tail -> Res.string.edit_cut_both
            head -> Res.string.edit_cut_head
            tail -> Res.string.edit_cut_tail
            else -> null
        }
    }

    /** Applies a drag of [handle] to [atUs]; the item's own rules clamp the result. */
    fun drag(item: VideoItem, handle: TimelineHandle, atUs: Long): VideoItem =
        when (handle) {
            TimelineHandle.TRIM_IN -> item.trimmed(atUs, item.trimOutUs)
            TimelineHandle.TRIM_OUT -> item.trimmed(item.trimInUs, atUs)
            TimelineHandle.SHOT_FLAG -> item.withShotStart(atUs)
            TimelineHandle.PLAYHEAD -> item
        }

    /** [us] pulled onto the nearest of [targets] within [pullUs], with the name of the one it took. */
    fun <N> snap(us: Long, targets: List<Pair<N, Long?>>, pullUs: Long): Pair<Long, N?> {
        val hit =
            targets
                .mapNotNull { (name, t) -> t?.let { name to it } }
                .filter { (_, t) -> abs(t - us) <= pullUs }
                .minByOrNull { (_, t) -> abs(t - us) }
        return if (hit == null) us to null else hit.second to hit.first
    }

    /** The ruler's tick spacing in seconds at [pxPerSecond]. */
    fun rulerStep(pxPerSecond: Float, minPx: Float): Float =
        rulerSteps.firstOrNull { it * pxPerSecond >= minPx } ?: rulerSteps.last()

    /** The playhead moved by one frame at [fps], kept inside the trim. */
    fun stepFrame(item: VideoItem, playheadUs: Long, frames: Int, fps: Float = 30f): Long =
        (playheadUs + (frames * 1e6f / fps).toLong()).coerceIn(item.trimInUs, item.trimOutUs)
}

/** Less than this between the trim and the shot is the clocks, not a cut (see [TimelineMath.shotCut]). */
const val SHOT_CUT_TOLERANCE_US = 300_000L
