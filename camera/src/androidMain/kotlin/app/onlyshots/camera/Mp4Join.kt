package app.onlyshots.camera

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins the end of one camera file to the whole of the next without re-encoding: the samples are copied as they are.
 * The recorder restarts its file now and then while armed, and a shot that starts just after a restart would otherwise
 * have no pre-roll; the end of the file before it holds the missing seconds.
 *
 * Both files come from the same recorder with the same settings, so their tracks match one for one. The cut in [first]
 * falls on the keyframe at or before [firstFromUs], so a little more than asked for may be kept.
 */
internal object Mp4Join {
    /**
     * Where the two parts landed in [out]: [firstCutUs] is the position in [first] that became 0 (subtract it from a
     * position in [first]), [secondOffsetUs] is where [second] starts (add it to a position in [second]).
     */
    class Joined(val firstCutUs: Long, val secondOffsetUs: Long)

    fun join(first: File, firstFromUs: Long, second: File, out: File): Joined {
        val a = MediaExtractor().apply { setDataSource(first.absolutePath) }
        val b = MediaExtractor().apply { setDataSource(second.absolutePath) }
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val tracks = tracksOf(a)
            require(tracks.map { it.mime } == tracksOf(b).map { it.mime }) { "the two files have different tracks" }
            tracks.firstOrNull { it.isVideo }?.rotation?.let(muxer::setOrientationHint)
            val outTrack = tracks.map { muxer.addTrack(it.format) }
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(BUFFER_BYTES)
            val info = MediaCodec.BufferInfo()

            // The end of the first file, from the keyframe at or before the cut.
            tracks.indices.forEach(a::selectTrack)
            a.seekTo(firstFromUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val videoIndex = tracks.indexOfFirst { it.isVideo }
            var cutUs = -1L
            var lastUs = 0L
            while (true) {
                val track = a.sampleTrackIndex
                if (track < 0) break
                val t = a.sampleTime
                // Time zero is the first video keyframe; audio from before it would play over nothing.
                if (cutUs < 0 && track == videoIndex) cutUs = t
                if (cutUs >= 0 && t >= cutUs) {
                    write(a, muxer, outTrack[track], buffer, info, t - cutUs)
                    lastUs = maxOf(lastUs, t - cutUs)
                }
                a.advance()
            }
            check(cutUs >= 0) { "no keyframe to start from" }

            // Then all of the second file, one frame after the first one's last.
            val offsetUs = lastUs + FRAME_US
            tracks.indices.forEach(b::selectTrack)
            var baseUs = -1L
            while (true) {
                val track = b.sampleTrackIndex
                if (track < 0) break
                val t = b.sampleTime
                if (baseUs < 0) baseUs = t
                write(b, muxer, outTrack[track], buffer, info, offsetUs + (t - baseUs).coerceAtLeast(0L))
                b.advance()
            }
            return Joined(cutUs, offsetUs)
        } finally {
            runCatching { muxer.stop() }
            muxer.release()
            a.release()
            b.release()
        }
    }

    @SuppressLint("WrongConstant")
    private fun write(
        from: MediaExtractor,
        muxer: MediaMuxer,
        track: Int,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        timeUs: Long,
    ) {
        buffer.clear()
        val size = from.readSampleData(buffer, 0)
        if (size < 0) return
        val flags = if (from.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        info.set(0, size, timeUs, flags)
        muxer.writeSampleData(track, buffer, info)
    }

    private class Track(val format: MediaFormat) {
        val mime: String = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val isVideo = mime.startsWith("video/")
        val rotation: Int? =
            if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else null
    }

    private fun tracksOf(e: MediaExtractor) = (0 until e.trackCount).map { Track(e.getTrackFormat(it)) }

    /** Room for the largest sample a 4K keyframe makes, with margin. */
    private const val BUFFER_BYTES = 8 * 1024 * 1024

    /** One frame at 30 fps: the step between the two files' samples. */
    private const val FRAME_US = 33_333L
}
