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
                val frame = r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return@withContext null
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
}
