package app.onlyshots.overlay

import app.onlyshots.core.model.NormRect

/**
 * Frame-number barcode for the export alignment check (handover: "decode the output and check overlay position and
 * timing at 3 timestamps within one frame").
 *
 * The spike's test clip carries its own frame number as [BITS] black/white blocks across the top [ROW_HEIGHT] of the
 * picture. In alignment mode the overlay renderer draws, in the same way, the frame number it believes it is drawing,
 * along the top of the overlay rect. Decoding an exported frame and reading both rows tells whether overlay and picture
 * agree.
 */
object AlignmentBarcode {
    const val BITS = 14

    /** Row height as a fraction of the frame height (60 px at 1920). */
    const val ROW_HEIGHT = 60f / 1920f

    /** Overlay rect used in alignment mode: full width, so the blocks line up with the clip's. */
    val SpikeRect = NormRect(0f, 0.55f, 1f, 0.3f)

    fun frameIndex(videoUs: Long, fps: Int): Int = ((videoUs * fps + 500_000) / 1_000_000).toInt()

    fun bit(frame: Int, i: Int): Boolean = (frame shr i) and 1 == 1

    /** Left edge of block [i] as a fraction of the frame width; bit 0 is the leftmost block. */
    fun blockLeft(i: Int): Float = i / BITS.toFloat()

    /** Centre of block [i] in a row whose top is at [rowTop] (fractions of the frame). */
    fun blockCentre(i: Int, rowTop: Float): Pair<Float, Float> = (i + 0.5f) / BITS to rowTop + ROW_HEIGHT / 2f

    fun pictureRowTop(): Float = 0f

    fun overlayRowTop(rect: NormRect): Float = rect.y

    /** Reads a frame number back from the luma (0..1) at each block centre. */
    fun decode(lumas: List<Float>): Int {
        require(lumas.size == BITS)
        var n = 0
        lumas.forEachIndexed { i, l -> if (l > 0.5f) n = n or (1 shl i) }
        return n
    }
}

/** One checked timestamp of an export. */
data class AlignmentSample(val atUs: Long, val pictureFrame: Int, val overlayFrame: Int) {
    val offsetFrames: Int
        get() = overlayFrame - pictureFrame
}
