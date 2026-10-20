@file:OptIn(ExperimentalForeignApi::class)

package app.onlyshots.video

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVLinearPCMIsNonInterleaved
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataCommonKeyCreationDate
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.commonKey
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.dateValue
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.stringValue
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMBlockBufferCopyDataBytes
import platform.CoreMedia.CMBlockBufferGetDataLength
import platform.CoreMedia.CMSampleBufferGetDataBuffer
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/** [VideoProbe] on AVFoundation: metadata, a JPEG thumbnail and mono PCM for the pump onset. */
class AvFoundationVideoProbe : VideoProbe {

    override suspend fun info(file: Path): VideoInfo =
        withContext(Dispatchers.Default) {
            val asset = AVURLAsset(NSURL.fileURLWithPath(file.toString()), null)
            val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
            // Phones store portrait video as rotated landscape: report the height as shown.
            val quarterTurned =
                track?.let {
                    it.preferredTransform.useContents {
                        val deg = atan2(b, a) * 180.0 / PI
                        ((((deg / 90.0).roundToInt() * 90) % 360 + 360) % 360) % 180 != 0
                    }
                } ?: false
            val shown = track?.naturalSize?.useContents { if (quarterTurned) width else height } ?: 0.0
            val across = track?.naturalSize?.useContents { if (quarterTurned) height else width } ?: 0.0
            VideoInfo(
                durationUs = (CMTimeGetSeconds(asset.duration) * 1_000_000).toLong(),
                hasAudio = asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty(),
                heightPx = shown.toInt(),
                widthPx = across.toInt(),
                recordedAt = recordedAt(asset),
            )
        }

    /** The container's creation date: what the camera stamped when it started recording. */
    private fun recordedAt(asset: AVURLAsset): Instant? {
        val item =
            asset.commonMetadata.filterIsInstance<AVMetadataItem>().firstOrNull {
                it.commonKey == AVMetadataCommonKeyCreationDate
            } ?: return null
        item.dateValue?.let {
            return Instant.fromEpochMilliseconds((it.timeIntervalSince1970 * 1000).toLong()).takeIf { t ->
                parseRecordedAt(t.toString()) != null
            }
        }
        return parseRecordedAt(item.stringValue)
    }

    override suspend fun writeThumbnail(file: Path, atUs: Long, out: Path, maxWidthPx: Int) {
        withContext(Dispatchers.Default) {
            val asset = AVURLAsset(NSURL.fileURLWithPath(file.toString()), null)
            val generator =
                AVAssetImageGenerator(asset).apply {
                    appliesPreferredTrackTransform = true
                    maximumSize = CGSizeMake(maxWidthPx.toDouble(), maxWidthPx.toDouble() * 16 / 9)
                }
            val cg = generator.copyCGImageAtTime(CMTimeMake(max(atUs, 0L), 1_000_000), null, null) ?: return@withContext
            val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(cg), 0.85) ?: return@withContext
            val bytes =
                ByteArray(jpeg.length.toInt()).also { array ->
                    array.usePinned { platform.posix.memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
                }
            FileSystem.SYSTEM.let { fs ->
                out.parent?.let { fs.createDirectories(it) }
                fs.write(out) { write(bytes) }
            }
        }
    }

    override suspend fun decodeMonoPcm(file: Path, maxSeconds: Int): MonoPcm? =
        withContext(Dispatchers.Default) {
            val asset = AVURLAsset(NSURL.fileURLWithPath(file.toString()), null)
            val track =
                asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return@withContext null
            val reader = AVAssetReader(asset, null) ?: return@withContext null
            // AVFoundation does the downmix and the rate conversion, so there is no DSP here.
            val output =
                AVAssetReaderTrackOutput(
                    track,
                    mapOf<Any?, Any?>(
                        AVFormatIDKey to kAudioFormatLinearPCM,
                        AVSampleRateKey to SAMPLE_RATE,
                        AVNumberOfChannelsKey to 1,
                        AVLinearPCMBitDepthKey to 32,
                        AVLinearPCMIsFloatKey to true,
                        AVLinearPCMIsBigEndianKey to false,
                        AVLinearPCMIsNonInterleaved to false,
                    ),
                )
            if (!reader.canAddOutput(output)) return@withContext null
            reader.addOutput(output)
            reader.startReading()
            val limit = SAMPLE_RATE * maxSeconds
            val out = ArrayList<Float>(minOf(limit, SAMPLE_RATE * 10))
            while (out.size < limit) {
                val sb = output.copyNextSampleBuffer() ?: break
                try {
                    val block = CMSampleBufferGetDataBuffer(sb) ?: continue
                    val length = CMBlockBufferGetDataLength(block).toInt()
                    val bytes = ByteArray(length)
                    bytes.usePinned { pinned ->
                        CMBlockBufferCopyDataBytes(block, 0u, length.toULong(), pinned.addressOf(0))
                    }
                    for (i in 0 until length / 4 step 1) {
                        if (out.size >= limit) break
                        out +=
                            Float.fromBits(
                                (bytes[i * 4].toInt() and 0xFF) or
                                    ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                                    ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                                    ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24)
                            )
                    }
                } finally {
                    CFRelease(sb)
                }
            }
            reader.cancelReading()
            if (out.isEmpty()) null else MonoPcm(out.toFloatArray(), SAMPLE_RATE)
        }

    // ponytail: every iPhone that can shoot 4K can also encode it; narrow this only if a device
    // turns up that cannot.
    override fun supports4k(): Boolean = true

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
