package app.onlyshots.core.model

import kotlinx.serialization.Serializable

/**
 * The shape of the video a user makes: Reels and Stories (9:16), the feed (4:5), square, or landscape (16:9). The
 * picture is cover-fitted into it and the graph placed in it; every size inside it is measured against its short side,
 * which is 390 reference px in every format.
 */
@Serializable
enum class VideoFormat(val w: Int, val h: Int, val label: String) {
    PORTRAIT_9_16(9, 16, "9:16"),
    FEED_4_5(4, 5, "4:5"),
    SQUARE_1_1(1, 1, "1:1"),
    LANDSCAPE_16_9(16, 9, "16:9");

    /** Width over height. */
    val ratio: Float
        get() = w.toFloat() / h

    val landscape: Boolean
        get() = w > h

    /** The frame in reference px: 390 on the short side. */
    val refW: Float
        get() = if (w <= h) REF else REF * w / h

    val refH: Float
        get() = if (w <= h) REF * h / w else REF

    /** Pixel size with [shortSide] px on the short side. */
    fun size(shortSide: Int): Size =
        if (w <= h) Size(shortSide, shortSide * h / w) else Size(shortSide * w / h, shortSide)

    companion object {
        /** The short side in reference px. */
        const val REF = 390f
    }
}

data class Size(val width: Int, val height: Int)

/**
 * Where output point ([fx], [fy]) (fractions of a [format] frame) comes from in a source picture of width / height
 * [sourceAspect], upright: the picture cover-fitted into the frame, centred, then [reframe] on top — what every
 * exporter draws. Fractions of the source.
 */
fun sourcePoint(fx: Float, fy: Float, sourceAspect: Float, format: VideoFormat, reframe: Reframe): Pair<Float, Float> {
    // Undo the reframe: back to the cover-fitted frame.
    val gx = (fx - 0.5f) / reframe.zoom + reframe.x
    val gy = (fy - 0.5f) / reframe.zoom + reframe.y
    val out = format.ratio
    // Cover: a wider source shows only the middle of its width, a taller one only the middle of its height.
    return if (sourceAspect > out) (0.5f + (gx - 0.5f) * out / sourceAspect) to gy
    else gx to (0.5f + (gy - 0.5f) * sourceAspect / out)
}
