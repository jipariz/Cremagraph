package app.onlyshots.video

import androidx.compose.ui.text.font.FontFamily
import app.onlyshots.core.model.Layout
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.model.style.GlassSettings
import app.onlyshots.core.model.style.GridMode
import app.onlyshots.core.model.style.GridStyle
import app.onlyshots.core.model.style.Material
import app.onlyshots.overlay.AlignmentBarcode
import app.onlyshots.overlay.GraphStyles
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

/** The export test of the phones, on the desktop editor's FFmpeg exporter, with the same spike clip. */
class FfmpegExportTest {
    private val clip = File(System.getProperty("onlyshots.spikeClip") ?: "testdata/${SpikeClip.NAME}").absolutePath
    private val exporter =
        FfmpegVideoExporter(videoPath = { File(it.file).toOkioPath() }, fonts = { FontFamily.Default })
    private val tmp = File(System.getProperty("java.io.tmpdir"))

    private fun export(trimInUs: Long, trimOutUs: Long, checkAtUs: List<Long>) = runBlocking {
        val out = File(tmp, "onlyshots-export-$trimInUs.mp4").toOkioPath()
        val mark = TimeSource.Monotonic.markNow()
        val last =
            exporter
                .export(
                    SpikeClip.item(clip, trimInUs, trimOutUs),
                    Quality.HD_1080,
                    withSound = true,
                    out = out,
                    alignmentBarcode = true,
                )
                .last()
        assertTrue(last is ExportProgress.Done, "export failed: $last")
        val samples = checkAlignment(FfmpegFrameSampler(), out, AlignmentBarcode.SpikeRect, checkAtUs)
        println(
            "OnlyShots desktop export ${trimInUs / 1000}-${trimOutUs / 1000} ms took ${mark.elapsedNow()}; $samples"
        )
        assertEquals(checkAtUs.size, samples.size)
        for (s in samples) {
            val expected = AlignmentBarcode.frameIndex(s.atUs + trimInUs, 30)
            assertTrue(abs(s.pictureFrame - expected) <= 1, "picture frame ${s.pictureFrame}, expected ≈ $expected")
            assertTrue(abs(s.offsetFrames) <= 1, "overlay off by ${s.offsetFrames} frames at ${s.atUs} µs")
        }
        val info = FfmpegVideoProbe().info(out)
        assertTrue(info.hasAudio, "the sound is kept")
        assertTrue(abs(info.durationUs - (trimOutUs - trimInUs)) < 200_000, "duration ${info.durationUs}")
    }

    @Test
    fun fullClipOverlayMatchesPicture() = export(0, SpikeClip.DURATION_US, listOf(5_000_000, 22_500_000, 40_000_000))

    @Test
    fun trimmedClipOverlayMatchesPicture() = export(2_000_000, 12_000_000, listOf(1_000_000, 5_000_000, 9_000_000))

    /** Blur and Liquid Glass panels over the clip's frame-number blocks: blurred, and the glass differs from it. */
    @Test
    fun blurAndGlassPanelsDrawIntoTheVideo() = runBlocking {
        val rect = NormRect(0f, 0f, 1f, 0.12f)
        val base = GraphStyles.Gaggiuino.copy(id = "u-test", grid = GridStyle(GridMode.NONE))
        fun style(m: Material) =
            base.copy(
                panel =
                    base.panel.copy(
                        material = m,
                        opacity = 0f,
                        blur = 40f,
                        glass = GlassSettings(frost = 40f, refraction = 0.5f, curve = 0.5f),
                    )
            )
        suspend fun lumas(m: Material, y: Float): List<Float> {
            val s = style(m)
            val item =
                SpikeClip.item(clip, 0, 6_000_000)
                    .copy(
                        shot = SampleShots.zer0.copy(profileName = ""),
                        overlay =
                            OverlaySettings(
                                styleId = s.id,
                                style = s,
                                rect = rect,
                                series = emptySet(),
                                layout = Layout.GRAPH_ONLY,
                            ),
                    )
            val out = File(tmp, "onlyshots-backdrop-$m.mp4").toOkioPath()
            val last = exporter.export(item, Quality.HD_1080, withSound = false, out = out).last()
            assertTrue(last is ExportProgress.Done, "export failed: $last")
            return FfmpegFrameSampler().lumaAt(out, 3_000_000, (0 until 140).map { (it + 0.5f) / 140f to y })!!
        }
        fun roughness(l: List<Float>) = l.zipWithNext { a, b -> abs(a - b) }.average()
        val row = AlignmentBarcode.ROW_HEIGHT / 2f
        val sharp = roughness(lumas(Material.NONE, row))
        val blurred = roughness(lumas(Material.BLUR, row))
        println("OnlyShots desktop barcode roughness: no panel $sharp, blur $blurred")
        assertTrue(blurred < sharp * 0.5, "blur $blurred should be well under $sharp")
        val edge = 0.12f - 8f / 1920f
        val difference =
            lumas(Material.GLASS, edge).zip(lumas(Material.BLUR, edge)).map { (a, b) -> abs(a - b) }.average()
        println("OnlyShots desktop glass against blur near the edge: $difference")
        assertTrue(difference > 0.01, "glass should differ from blur near its edge ($difference)")
    }

    @Test
    fun aReframedExportZoomsAsThePreviewDoes() = runBlocking {
        val r = checkReframe(exporter, FfmpegFrameSampler(), clip, tmp.toOkioPath())
        println("OnlyShots desktop reframe: $r")
        assertTrue(r.mapped < 0.06 && r.mapped < r.unmapped * 0.5, "$r")
    }

    @Test
    fun everyFormatComesOutAtItsSize() = runBlocking {
        for (f in VideoFormat.entries) {
            val size = Quality.HD_1080.size(f)
            assertEquals(
                size.width to size.height,
                exportedSize(exporter, FfmpegVideoProbe(), clip, tmp.toOkioPath(), f),
            )
        }
    }

    @Test
    fun aSquareAndALandscapeExportCropAsThePreviewDoes() = runBlocking {
        for (f in listOf(VideoFormat.SQUARE_1_1, VideoFormat.LANDSCAPE_16_9)) {
            val r = checkReframe(exporter, FfmpegFrameSampler(), clip, tmp.toOkioPath(), f)
            println("OnlyShots desktop $f: $r")
            assertTrue(r.mapped < 0.06 && r.mapped < r.unmapped * 0.5, "$f: $r")
        }
    }

    @Test
    fun aLandscapeCropCanMoveAlongThePortraitPicture() = runBlocking {
        val r =
            checkReframe(
                exporter,
                FfmpegFrameSampler(),
                clip,
                tmp.toOkioPath(),
                VideoFormat.LANDSCAPE_16_9,
                SpikeLandscapeTop,
            )
        println("OnlyShots desktop pan: $r")
        assertTrue(r.mapped < 0.06 && r.mapped < r.unmapped * 0.5, "$r")
    }
}
