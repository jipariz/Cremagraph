package app.onlyshots.video

import android.graphics.Color
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path

class AndroidFrameSampler : FrameSampler {
    override suspend fun lumaAt(file: Path, atUs: Long, points: List<Pair<Float, Float>>): List<Float>? =
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.toString())
                val frame =
                    r.scaledFrameAt(atUs, MediaMetadataRetriever.OPTION_CLOSEST, SAMPLE_WIDTH_PX)
                        ?: return@withContext null
                points.map { (x, y) ->
                    val c = frame.getPixel(
                        (x * frame.width).toInt().coerceIn(0, frame.width - 1),
                        (y * frame.height).toInt().coerceIn(0, frame.height - 1),
                    )
                    (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f
                }
            } finally {
                r.release()
            }
        }

    private companion object {
        /**
         * How wide a frame has to be to read a handful of points off it. A whole decoded frame is wanted nowhere
         * here: [app.onlyshots.video.checkAlignment] reads fourteen barcode block centres and `LumaTrack` reads
         * twelve, every 250 ms of a take, so a 4 K source was decoding 33 MB a sample.
         *
         * The bound is set by the barcode, not by the luma: [app.onlyshots.overlay.AlignmentBarcode.BITS] is 14, so
         * a block is a fourteenth of the width and its row is 60/1920 of the height. At this width a block is still
         * about 34 px across and the row about 27 px tall, which centre sampling reads with room to spare.
         */
        const val SAMPLE_WIDTH_PX = 480
    }
}
