package app.onlyshots.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** [VideoProbe] on `MediaMetadataRetriever` for metadata and `MediaCodec` for the audio track. */
class AndroidVideoProbe(private val fs: FileSystem = FileSystem.SYSTEM) : VideoProbe {

    override suspend fun info(file: Path): VideoInfo = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.toString())
            fun key(k: Int) = r.extractMetadata(k)
            val rotation = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val width = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            VideoInfo(
                durationUs = (key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1_000,
                hasAudio = key(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                // A portrait clip is stored landscape with a rotation tag; report what it shows.
                heightPx = if (rotation % 180 == 0) height else width,
                widthPx = if (rotation % 180 == 0) width else height,
                recordedAt = parseRecordedAt(key(MediaMetadataRetriever.METADATA_KEY_DATE)),
            )
        } finally {
            r.release()
        }
    }

    override suspend fun writeThumbnail(file: Path, atUs: Long, out: Path, maxWidthPx: Int) {
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            val frame = try {
                r.setDataSource(file.toString())
                r.scaledFrameAt(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxWidthPx)
            } finally {
                r.release()
            } ?: return@withContext
            // Already the right size from API 27 on; on 26 the decoder gave the whole frame and it still has to shrink.
            val scale = (maxWidthPx.toFloat() / frame.width).coerceAtMost(1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
            } else {
                frame
            }
            val bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
            out.parent?.let { fs.createDirectories(it) }
            fs.sink(out).buffer().use { it.write(bytes) }
        }
    }

    override suspend fun decodeMonoPcm(file: Path, maxSeconds: Int): MonoPcm? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.toString())
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val limit = rate.toLong() * maxSeconds
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
                configure(format, null, null, 0)
                start()
            }
            val out = ArrayList<Float>(minOf(limit, rate * 10L).toInt())
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            while (out.size < limit) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                    // 16-bit PCM, channels interleaved: average them down to mono.
                    while (buffer.hasRemaining() && out.size < limit) {
                        var sum = 0f
                        for (c in 0 until channels) sum += if (buffer.hasRemaining()) buffer.get() / 32768f else 0f
                        out += sum / channels
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }
            if (out.isEmpty()) null else MonoPcm(out.toFloatArray(), rate)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    override fun supports4k(): Boolean = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } &&
            runCatching {
                info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities?.isSizeSupported(3840, 2160) == true
            }.getOrDefault(false)
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
