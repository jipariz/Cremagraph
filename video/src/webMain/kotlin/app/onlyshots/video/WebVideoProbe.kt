package app.onlyshots.video

import kotlin.time.Instant
import okio.FileSystem
import okio.Path

/** [VideoProbe] in the browser: Mediabunny for the metadata and frames, Web Audio for the sound. */
class WebVideoProbe(private val fs: FileSystem = WebFiles.fs) : VideoProbe {
    override suspend fun info(file: Path): VideoInfo {
        val p = WebMedia.probe(file.toString())
        return VideoInfo(
            durationUs = p.durationUs,
            hasAudio = p.hasAudio,
            heightPx = p.height,
            widthPx = p.width,
            recordedAt =
                p.createdMs
                    .takeIf { it > 0 }
                    ?.let { Instant.fromEpochMilliseconds(it.toLong()) }
                    ?.takeIf { parseRecordedAt(it.toString()) != null },
        )
    }

    override suspend fun writeThumbnail(file: Path, atUs: Long, out: Path, maxWidthPx: Int) {
        val jpeg = WebMedia.thumbnailJpeg(file.toString(), atUs / 1_000_000.0, maxWidthPx)
        out.parent?.let(fs::createDirectories)
        fs.write(out) { write(jpeg) }
    }

    override suspend fun decodeMonoPcm(file: Path, maxSeconds: Int): MonoPcm? =
        WebMedia.decodeAudio(file.toString(), maxSeconds)

    /** The web export is 1080 on the short side. */
    override fun supports4k(): Boolean = false
}

/** Luma at points of the frame shown at a time, in the browser: for styles that adapt to the video. */
class WebFrameSampler : FrameSampler {
    override suspend fun lumaAt(file: Path, atUs: Long, points: List<Pair<Float, Float>>): List<Float>? = runCatching {
        WebMedia.lumaAt(file.toString(), atUs / 1_000_000.0, points)
    }
        .getOrNull()
}
