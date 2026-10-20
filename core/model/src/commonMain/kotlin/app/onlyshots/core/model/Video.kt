package app.onlyshots.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class Origin {
    RECORDED,
    IMPORTED,
}

@Serializable
enum class Quality(val shortSide: Int) {
    HD_1080(1080),
    UHD_4K(2160);

    /** The output's pixel size in [format]: 1080 × 1920 at 9:16, 1920 × 1080 at 16:9, 1080 × 1350 at 4:5. */
    fun size(format: VideoFormat): Size = format.size(shortSide)
}

@Serializable
data class VideoItem(
    val id: String,
    /** File name of the video inside the library folder. */
    val file: String,
    val createdAt: Instant,
    val durationUs: Long,
    val origin: Origin,
    val hasAudio: Boolean,
    val shot: Shot?,
    /** Video time of graph time 0; null until an imported video is synced. */
    val shotStartInVideoUs: Long?,
    /** True for in-app recordings: the shot-start flag cannot be moved until [unlocked]. */
    val syncLocked: Boolean,
    val trimInUs: Long,
    val trimOutUs: Long,
    val overlay: OverlaySettings,
    /** Zoom and crop of the picture inside the [format] frame; the identity keeps the whole picture, cover-fitted. */
    val reframe: Reframe = Reframe(),
    /** When the camera recorded the video, from the file itself; for matching a shot by its clock. */
    val recordedAt: Instant? = null,
    /** The shape of the video this makes; 9:16 for everything made before there was a choice. */
    val format: VideoFormat = VideoFormat.PORTRAIT_9_16,
) {
    val isLinked: Boolean
        get() = shot != null && shotStartInVideoUs != null

    val trimmedDurationUs: Long
        get() = trimOutUs - trimInUs

    /** Video time where the shot ends; null until the shot has a start. */
    val shotEndInVideoUs: Long?
        get() = shotStartInVideoUs?.let { start -> shot?.let { start + it.durationUs } }

    /** Moves the trim handles; keeps at least [MIN_TRIM_US] between them. */
    fun trimmed(inUs: Long, outUs: Long): VideoItem {
        val i = inUs.coerceIn(0L, (durationUs - MIN_TRIM_US).coerceAtLeast(0L))
        val o = outUs.coerceIn((i + MIN_TRIM_US).coerceAtMost(durationUs), durationUs)
        return copy(trimInUs = i, trimOutUs = o)
    }

    /** Moves the shot-start flag. Ignored for synced in-app recordings. */
    fun withShotStart(videoUs: Long): VideoItem =
        if (syncLocked) this else copy(shotStartInVideoUs = videoUs.coerceIn(0L, durationUs))

    /** A recording's shot start, freed to be moved by hand; it stays where it was recorded until it is moved. */
    fun unlocked(): VideoItem = copy(syncLocked = false)

    /**
     * Where the shot starts by the clocks, the shot's against the video's: null when either clock is unknown or the
     * shot would start outside the clip (a file whose clock is off, or a shot pulled before the camera ran).
     */
    fun shotStartByClock(): Long? {
        val shot = shot ?: return null
        val recorded = recordedAt ?: return null
        val offsetUs = (shot.startedAt - recorded).inWholeMicroseconds
        return offsetUs.takeIf { it in 0L..durationUs }
    }

    companion object {
        const val MIN_TRIM_US = 3_000_000L
    }
}

/**
 * The picture zoomed by [zoom] (1 to [MAX_ZOOM]) around ([x], [y]), the centre of what is kept, as fractions of the
 * output frame with the picture cover-fitted in it: where the picture reaches past the frame (a 9:16 clip in a 16:9
 * video), the centre can lie outside 0..1. It is kept far enough in that the picture always covers the frame
 * ([clamped]).
 */
@Serializable
data class Reframe(val zoom: Float = 1f, val x: Float = 0.5f, val y: Float = 0.5f) {
    /** The picture cover-fitted and centred: nothing to zoom or move. */
    val isIdentity: Boolean
        get() = zoom == 1f && x == 0.5f && y == 0.5f

    /**
     * The same reframe with [zoom] in range and the centre held where the frame still shows only picture. The picture
     * is cover-fitted into a [format] frame, so a source of a different shape ([sourceAspect], width over height)
     * reaches past the frame on one axis, and the crop can move along it; without a known shape, only zooming in makes
     * room.
     */
    fun clamped(sourceAspect: Float? = null, format: VideoFormat = VideoFormat.PORTRAIT_9_16): Reframe {
        val z = zoom.coerceIn(1f, MAX_ZOOM)
        val half = 0.5f / z
        // How far the cover-fitted picture reaches, in frame widths and heights: 1 on the axis it fills exactly.
        val spanX = sourceAspect?.let { maxOf(1f, it / format.ratio) } ?: 1f
        val spanY = sourceAspect?.let { maxOf(1f, format.ratio / it) } ?: 1f
        fun hold(c: Float, span: Float) = c.coerceIn(0.5f - span / 2f + half, 0.5f + span / 2f - half)
        return Reframe(z, hold(x, spanX), hold(y, spanY))
    }

    /** Where frame point ([fx], [fy]) of the picture lands in the output, both as fractions of the frame. */
    fun map(fx: Float, fy: Float): Pair<Float, Float> = ((fx - x) * zoom + 0.5f) to ((fy - y) * zoom + 0.5f)

    companion object {
        const val MAX_ZOOM = 3f
    }
}
