package app.onlyshots.video

import app.onlyshots.core.model.PumpOnsetDetector
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.sourcePoint
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import okio.Path

sealed interface ExportProgress {
    /** 0..1 of the trimmed duration rendered. */
    data class Rendering(val fraction: Float) : ExportProgress

    data class Done(val file: Path) : ExportProgress

    data class Failed(val reason: String) : ExportProgress
}

/**
 * Renders the overlay into an H.264 (+ AAC) MP4 on device. Platform implementations: Media3 Transformer with
 * `ClippingConfiguration` and an overlay effect on Android, an AVFoundation custom compositor on iOS. Both draw each
 * frame's overlay with `app.onlyshots.overlay.compose.OverlayFrameRenderer` (only the overlay rect), so export equals
 * preview. Sound is included only when [withSound] and the video has an audio track.
 */
interface VideoExporter {
    /**
     * @param alignmentBarcode draws the frame-number barcode (see `AlignmentBarcode`) for the export alignment check;
     *   never on in a user-facing export.
     */
    fun export(
        item: VideoItem,
        quality: Quality,
        withSound: Boolean,
        out: Path,
        alignmentBarcode: Boolean = false,
    ): Flow<ExportProgress>
}

/**
 * Points of [this] video's output frame, mapped into its source picture of [sourceAspect]: the cover crop and the
 * reframe, as the exporters draw them. Unchanged when the source's shape is not known.
 */
fun VideoItem.sourceMapping(sourceAspect: Float?): (Float, Float) -> Pair<Float, Float> =
    if (sourceAspect == null) { x, y -> x to y } else { x, y -> sourcePoint(x, y, sourceAspect, format, reframe) }

/** 4K is offered only when the source is 4K (its short side) and the device encoder supports it. */
fun availableQualities(sourceShortSide: Int, encoderSupports4k: Boolean): List<Quality> =
    if (sourceShortSide >= Quality.UHD_4K.shortSide && encoderSupports4k) Quality.entries else listOf(Quality.HD_1080)

/** What one open of a video file tells us about it. */
data class VideoInfo(
    val durationUs: Long,
    val hasAudio: Boolean,
    val heightPx: Int,
    /** When the camera started recording, from the file's own metadata; null when it doesn't say. */
    val recordedAt: Instant? = null,
    /** The width as shown (after rotation), 0 when unknown. */
    val widthPx: Int = 0,
) {
    /** Width over height as shown, or null when a side is unknown. */
    val aspect: Float?
        get() = if (widthPx > 0 && heightPx > 0) widthPx.toFloat() / heightPx else null

    /** The shorter side as shown, for the 4K check. */
    val shortSidePx: Int
        get() = if (widthPx > 0) minOf(widthPx, heightPx) else heightPx
}

/** Mono audio at whatever rate the decoder gave us; [PumpOnsetDetector] takes both. */
class MonoPcm(val samples: FloatArray, val sampleRate: Int)

/** Thumbnails, duration, audio and encoder support of a library file. */
interface VideoProbe {
    suspend fun info(file: Path): VideoInfo

    suspend fun writeThumbnail(file: Path, atUs: Long, out: Path, maxWidthPx: Int = 512)

    /** The first [maxSeconds] of audio, mono, for [PumpOnsetDetector]; null without audio. */
    suspend fun decodeMonoPcm(file: Path, maxSeconds: Int = 60): MonoPcm?

    /** Whether this device's H.264 encoder can write 2160 × 3840. */
    fun supports4k(): Boolean
}

/**
 * The file opened but its picture cannot be decoded here: [codec] as the decoder names it ("hevc", "prores"…), empty
 * when nothing in the file was read as video. Only the browser raises it; the screen that picked the file words it.
 */
class UndecodableVideoException(val codec: String) : IllegalStateException("cannot decode '$codec'")

/** A codec in the words people know it by; one with no such name is shown as it came. */
fun codecName(codec: String): String =
    when (codec) {
        "avc" -> "H.264"
        "hevc" -> "HEVC"
        "prores" -> "ProRes"
        else -> codec.uppercase()
    }
