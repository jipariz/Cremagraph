package app.onlyshots.video

import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Reframe
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.sourcePoint
import kotlin.math.abs
import kotlinx.coroutines.flow.last
import okio.Path

/** How well a reframed export agrees with the plain one: at the points [Reframe.map] predicts, and where it doesn't. */
class ReframeAgreement(val mapped: Double, val unmapped: Double) {
    override fun toString() = "mean luma difference: mapped $mapped, unmapped $unmapped"
}

/**
 * A 16:9 crop of the 9:16 spike clip moved up past the frame's edge, to the clip's top band: barcode and frame number.
 * Only reachable because the crop can travel the whole height the picture has.
 */
val SpikeLandscapeTop = Reframe(zoom = 1f, x = 0.5f, y = -0.5f)

/** Zoomed in on the spike clip's barcode row and frame number, where the picture has edges to line up. */
val SpikeReframe = Reframe(zoom = 2f, x = 0.25f, y = 0.25f)

/**
 * Exports the first seconds of the spike clip as it is and in [format] through [SpikeReframe], with no graph, and
 * compares their brightness at 3 s: each point of the reframed export against the plain export where the cover crop and
 * [Reframe.map] say it came from ([sourcePoint], [ReframeAgreement.mapped]), and against the same point
 * ([ReframeAgreement.unmapped]). An exporter that draws what the preview shows gets the first close to zero and well
 * under the second.
 */
suspend fun checkReframe(
    exporter: VideoExporter,
    sampler: FrameSampler,
    clipPath: String,
    outDir: Path,
    format: VideoFormat = VideoFormat.PORTRAIT_9_16,
    reframe: Reframe = SpikeReframe,
): ReframeAgreement {
    val plainItem = SpikeClip.item(clipPath, 0, 5_000_000).copy(shot = null, shotStartInVideoUs = null)
    suspend fun export(name: String, reframe: Reframe, format: VideoFormat): Path {
        val out = outDir / "reframe-$name.mp4"
        val item = plainItem.copy(reframe = reframe, format = format)
        val last = exporter.export(item, Quality.HD_1080, withSound = false, out = out).last()
        check(last is ExportProgress.Done) { "export failed: $last" }
        return out
    }
    val plain = export("plain", Reframe(), VideoFormat.PORTRAIT_9_16)
    val zoomed = export("zoomed-${format.name}-${reframe.y}", reframe, format)
    val outputs = buildList { for (j in 0 until 24) for (i in 0 until 24) add((i + 0.5f) / 24 to (j + 0.5f) / 24) }
    // Where each output point was in the picture: the 9:16 clip cover-fitted into the format, then the reframe.
    val sources = outputs.map { (x, y) -> sourcePoint(x, y, 9f / 16f, format, reframe) }
    val atUs = 3_000_000L
    val seen = sampler.lumaAt(zoomed, atUs, outputs) ?: error("no frame in the reframed export")
    val expected = sampler.lumaAt(plain, atUs, sources) ?: error("no frame in the plain export")
    val same = sampler.lumaAt(plain, atUs, outputs) ?: error("no frame in the plain export")
    return ReframeAgreement(
        mapped = seen.zip(expected).map { (a, b) -> abs(a - b).toDouble() }.average(),
        unmapped = seen.zip(same).map { (a, b) -> abs(a - b).toDouble() }.average(),
    )
}

/** Exports the spike clip's first seconds in [format] and returns the file's width and height as a probe reads them. */
suspend fun exportedSize(
    exporter: VideoExporter,
    probe: VideoProbe,
    clipPath: String,
    outDir: Path,
    format: VideoFormat,
): Pair<Int, Int> {
    val out = outDir / "format-${format.name}.mp4"
    val item = SpikeClip.item(clipPath, 0, 3_000_000).copy(format = format)
    val last = exporter.export(item, Quality.HD_1080, withSound = false, out = out).last()
    check(last is ExportProgress.Done) { "export failed: $last" }
    val info = probe.info(out)
    return info.widthPx to info.heightPx
}
