package app.onlyshots.video

import app.onlyshots.core.model.NormRect
import app.onlyshots.overlay.AlignmentBarcode
import app.onlyshots.overlay.AlignmentSample
import okio.Path

/** Reads decoded pixels of a video file, for export tests. */
interface FrameSampler {
    /**
     * Luma (0..1) at each point (fractions of the frame) of the frame shown at [atUs], or null if no frame could be
     * decoded there.
     */
    suspend fun lumaAt(file: Path, atUs: Long, points: List<Pair<Float, Float>>): List<Float>?
}

/**
 * The export test from the handover: decode the output and check that the overlay agrees with the picture at a few
 * timestamps. Both barcodes come from the same decoded frame, so any frame near [timesUs] will do; equal numbers mean
 * the overlay was drawn for exactly that frame.
 */
suspend fun checkAlignment(
    sampler: FrameSampler,
    file: Path,
    overlayRect: NormRect,
    timesUs: List<Long>,
): List<AlignmentSample> = timesUs.mapNotNull { at ->
    val picturePoints =
        (0 until AlignmentBarcode.BITS).map { AlignmentBarcode.blockCentre(it, AlignmentBarcode.pictureRowTop()) }
    val overlayPoints =
        (0 until AlignmentBarcode.BITS).map {
            AlignmentBarcode.blockCentre(it, AlignmentBarcode.overlayRowTop(overlayRect))
        }
    val lumas = sampler.lumaAt(file, at, picturePoints + overlayPoints) ?: return@mapNotNull null
    AlignmentSample(
        atUs = at,
        pictureFrame = AlignmentBarcode.decode(lumas.subList(0, AlignmentBarcode.BITS)),
        overlayFrame = AlignmentBarcode.decode(lumas.subList(AlignmentBarcode.BITS, lumas.size)),
    )
}
