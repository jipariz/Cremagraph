package app.onlyshots.video

import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.overlay.AlignmentBarcode
import app.onlyshots.overlay.compose.OverlayFonts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory

/**
 * The handover's export test on the simulator. Gradle passes the clip's path in ONLYSHOTS_SPIKE_CLIP (see
 * video/build.gradle.kts); without it the test is skipped.
 */
class AvFoundationExportTest {
    private val clip = NSProcessInfo.processInfo.environment["ONLYSHOTS_SPIKE_CLIP"] as? String
    private val exporter = AvFoundationVideoExporter(videoPath = { it.file.toPath() }, fonts = { OverlayFonts.Default })

    private fun export(trimInUs: Long, trimOutUs: Long, checkAtUs: List<Long>) = runBlocking {
        val path =
            clip
                ?: run {
                    println("ONLYSHOTS_SPIKE_CLIP not set, skipping")
                    return@runBlocking
                }
        val out = (NSTemporaryDirectory() + "export-$trimInUs.mp4").toPath()
        val mark = TimeSource.Monotonic.markNow()
        val last =
            exporter
                .export(
                    SpikeClip.item(path, trimInUs, trimOutUs),
                    Quality.HD_1080,
                    withSound = true,
                    out = out,
                    alignmentBarcode = true,
                )
                .last()
        assertTrue(last is ExportProgress.Done, "export failed: $last")
        val samples = checkAlignment(IosFrameSampler(), out, AlignmentBarcode.SpikeRect, checkAtUs)
        println(
            "OnlyShots export ${trimInUs / 1000}-${trimOutUs / 1000} ms took ${mark.elapsedNow()}; alignment $samples"
        )
        assertEquals(checkAtUs.size, samples.size)
        for (s in samples) {
            val expected = AlignmentBarcode.frameIndex(s.atUs + trimInUs, 30)
            assertTrue(abs(s.pictureFrame - expected) <= 1, "picture frame ${s.pictureFrame}, expected ≈ $expected")
            assertTrue(abs(s.offsetFrames) <= 1, "overlay off by ${s.offsetFrames} frames at ${s.atUs} µs")
        }
    }

    @Test
    fun fullClipOverlayMatchesPicture() = export(0, SpikeClip.DURATION_US, listOf(5_000_000, 22_500_000, 40_000_000))

    @Test
    fun trimmedClipOverlayMatchesPicture() = export(2_000_000, 12_000_000, listOf(1_000_000, 5_000_000, 9_000_000))

    /** Blur and Liquid Glass panels over the clip's frame-number blocks: blurred, and the glass differs from it. */
    @Test
    fun blurAndGlassPanelsDrawIntoTheVideo() = runBlocking {
        val path = clip ?: return@runBlocking
        val rect = app.onlyshots.core.model.NormRect(0f, 0f, 1f, 0.12f)
        val base =
            app.onlyshots.overlay.GraphStyles.Gaggiuino.copy(
                id = "u-test",
                grid = app.onlyshots.core.model.style.GridStyle(app.onlyshots.core.model.style.GridMode.NONE),
            )
        fun style(m: app.onlyshots.core.model.style.Material) =
            base.copy(
                panel =
                    base.panel.copy(
                        material = m,
                        opacity = 0f,
                        blur = 40f,
                        glass =
                            app.onlyshots.core.model.style.GlassSettings(frost = 40f, refraction = 0.5f, curve = 0.5f),
                    )
            )
        suspend fun lumas(m: app.onlyshots.core.model.style.Material, y: Float): List<Float> {
            val s = style(m)
            val item =
                SpikeClip.item(path, 0, 6_000_000)
                    .copy(
                        shot = app.onlyshots.core.model.sample.SampleShots.zer0.copy(profileName = ""),
                        overlay =
                            app.onlyshots.core.model.OverlaySettings(
                                styleId = s.id,
                                style = s,
                                rect = rect,
                                series = emptySet(),
                                layout = app.onlyshots.core.model.Layout.GRAPH_ONLY,
                            ),
                    )
            val out = (NSTemporaryDirectory() + "backdrop-$m.mp4").toPath()
            val last = exporter.export(item, Quality.HD_1080, withSound = false, out = out).last()
            assertTrue(last is ExportProgress.Done, "export failed: $last")
            return IosFrameSampler().lumaAt(out, 3_000_000, (0 until 140).map { (it + 0.5f) / 140f to y })!!
        }
        fun roughness(l: List<Float>) = l.zipWithNext { a, b -> abs(a - b) }.average()
        val row = AlignmentBarcode.ROW_HEIGHT / 2f
        val sharp = roughness(lumas(app.onlyshots.core.model.style.Material.NONE, row))
        val blurred = roughness(lumas(app.onlyshots.core.model.style.Material.BLUR, row))
        println("OnlyShots iOS barcode roughness: no panel $sharp, blur $blurred")
        assertTrue(blurred < sharp * 0.5, "blur $blurred should be well under $sharp")
        val edge = 0.12f - 8f / 1920f
        val difference =
            lumas(app.onlyshots.core.model.style.Material.GLASS, edge)
                .zip(lumas(app.onlyshots.core.model.style.Material.BLUR, edge))
                .map { (a, b) -> abs(a - b) }
                .average()
        println("OnlyShots iOS glass against blur near the edge: $difference")
        assertTrue(difference > 0.01, "glass should differ from blur near its edge ($difference)")
    }

    @Test
    fun aReframedExportZoomsAsThePreviewDoes() = runBlocking {
        val path =
            clip
                ?: run {
                    println("ONLYSHOTS_SPIKE_CLIP not set, skipping")
                    return@runBlocking
                }
        val r = checkReframe(exporter, IosFrameSampler(), path, NSTemporaryDirectory().toPath())
        println("OnlyShots iOS reframe: $r")
        assertTrue(r.mapped < 0.06 && r.mapped < r.unmapped * 0.5, "$r")
    }

    @Test
    fun everyFormatComesOutAtItsSize() = runBlocking {
        val path = clip ?: return@runBlocking
        for (f in VideoFormat.entries) {
            val size = Quality.HD_1080.size(f)
            assertEquals(
                size.width to size.height,
                exportedSize(exporter, AvFoundationVideoProbe(), path, NSTemporaryDirectory().toPath(), f),
            )
        }
    }

    @Test
    fun aSquareAndALandscapeExportCropAsThePreviewDoes() = runBlocking {
        val path = clip ?: return@runBlocking
        for (f in listOf(VideoFormat.SQUARE_1_1, VideoFormat.LANDSCAPE_16_9)) {
            val r = checkReframe(exporter, IosFrameSampler(), path, NSTemporaryDirectory().toPath(), f)
            println("OnlyShots iOS $f: $r")
            assertTrue(r.mapped < 0.06 && r.mapped < r.unmapped * 0.5, "$f: $r")
        }
    }

    @Test
    fun aLandscapeCropCanMoveAlongThePortraitPicture() = runBlocking {
        val path = clip ?: return@runBlocking
        val r =
            checkReframe(
                exporter,
                IosFrameSampler(),
                path,
                NSTemporaryDirectory().toPath(),
                VideoFormat.LANDSCAPE_16_9,
                SpikeLandscapeTop,
            )
        println("OnlyShots iOS pan: $r")
        assertTrue(r.mapped < 0.06 && r.mapped < r.unmapped * 0.5, "$r")
    }
}
