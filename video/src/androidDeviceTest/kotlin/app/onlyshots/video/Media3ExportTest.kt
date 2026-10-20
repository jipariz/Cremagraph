package app.onlyshots.video

import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.overlay.AlignmentBarcode
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** The handover's export test on a device: decode the output, overlay within one frame. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class Media3ExportTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val clip: File = File(target.cacheDir, SpikeClip.NAME).also { f ->
        InstrumentationRegistry.getInstrumentation().context.assets.open(SpikeClip.NAME).use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
    }
    private val exporter = Media3VideoExporter(target, videoPath = { it.file.toPath() }, fonts = { FontFamily.Default })

    private fun export(trimInUs: Long, trimOutUs: Long, checkAtUs: List<Long>) = runBlocking {
        val out = File(target.cacheDir, "export-$trimInUs.mp4").toOkioPath()
        val item = SpikeClip.item(clip.absolutePath, trimInUs, trimOutUs)
        val started = System.nanoTime()
        val last = exporter.export(item, Quality.HD_1080, withSound = true, out = out, alignmentBarcode = true).last()
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("export failed: $last", last is ExportProgress.Done)
        val samples = checkAlignment(AndroidFrameSampler(), out, AlignmentBarcode.SpikeRect, checkAtUs)
        Log.i("OnlyShots", "export ${trimInUs / 1000}-${trimOutUs / 1000} ms took $ms ms; alignment $samples")
        assertEquals(checkAtUs.size, samples.size)
        for (s in samples) {
            // The picture shows the clip's own frame: output time + trim start.
            val expected = AlignmentBarcode.frameIndex(s.atUs + trimInUs, 30)
            assertTrue("picture frame ${s.pictureFrame}, expected ≈ $expected", abs(s.pictureFrame - expected) <= 1)
            assertTrue("overlay off by ${s.offsetFrames} frames at ${s.atUs} µs", abs(s.offsetFrames) <= 1)
        }
    }

    /**
     * A blur panel over the clip's frame-number blocks: in the export the blocks under it are smeared, where with no
     * panel they stay sharp. Measured as the mean step in luma between neighbouring points along the block row.
     */
    @Test
    fun aBlurPanelBlursTheVideoBehindIt() = runBlocking {
        val rect = app.onlyshots.core.model.NormRect(0f, 0f, 1f, 0.12f)
        val clear =
            app.onlyshots.overlay.GraphStyles.Gaggiuino.copy(
                id = "u-test",
                panel =
                    app.onlyshots.overlay.GraphStyles.Gaggiuino.panel.copy(
                        material = app.onlyshots.core.model.style.Material.BLUR,
                        opacity = 0f,
                        blur = 40f,
                    ),
                grid = app.onlyshots.core.model.style.GridStyle(app.onlyshots.core.model.style.GridMode.NONE),
            )
        val none =
            clear.copy(panel = clear.panel.copy(material = app.onlyshots.core.model.style.Material.NONE))
        fun item(style: app.onlyshots.core.model.style.GraphStyle) =
            SpikeClip.item(clip.absolutePath, 0, 6_000_000)
                .copy(
                    // Nothing drawn on top — no curves, legend or readings — so only the video's blocks are measured.
                    shot = app.onlyshots.core.model.sample.SampleShots.zer0.copy(profileName = ""),
                    overlay =
                        app.onlyshots.core.model.OverlaySettings(
                            styleId = style.id,
                            style = style,
                            rect = rect,
                            series = emptySet(),
                            layout = app.onlyshots.core.model.Layout.GRAPH_ONLY,
                        ),
                )
        suspend fun lumas(style: app.onlyshots.core.model.style.GraphStyle, y: Float): List<Float> {
            val out = File(target.cacheDir, "edge-${style.panel.material}.mp4").toOkioPath()
            val last = exporter.export(item(style), Quality.HD_1080, withSound = false, out = out).last()
            assertTrue("export failed: $last", last is ExportProgress.Done)
            val points = (0 until 140).map { (it + 0.5f) / 140f to y }
            return AndroidFrameSampler().lumaAt(out, 3_000_000, points) ?: error("no frame")
        }
        suspend fun roughness(style: app.onlyshots.core.model.style.GraphStyle): Float {
            val out = File(target.cacheDir, "backdrop-${style.panel.material}.mp4").toOkioPath()
            val last = exporter.export(item(style), Quality.HD_1080, withSound = false, out = out).last()
            assertTrue("export failed: $last", last is ExportProgress.Done)
            val y = AlignmentBarcode.ROW_HEIGHT / 2f
            val points = (0 until 140).map { (it + 0.5f) / 140f to y }
            val lumas = AndroidFrameSampler().lumaAt(out, 3_000_000, points) ?: error("no frame")
            return lumas.zipWithNext { a, b -> abs(a - b) }.average().toFloat()
        }
        val sharp = roughness(none)
        val blurred = roughness(clear)
        // Liquid Glass: the same panel through Haze's glass shader. Refraction bends the blocks near the edge, so the
        // row differs from the plain blur; the export must also compile and run the shader on this GPU.
        val glass =
            clear.copy(
                panel =
                    clear.panel.copy(
                        material = app.onlyshots.core.model.style.Material.GLASS,
                        glass =
                            app.onlyshots.core.model.style.GlassSettings(
                                frost = clear.panel.blur,
                                refraction = 0.5f,
                                curve = 0.5f,
                            ),
                    )
            )
        // Near the panel's lower edge, where the rim bends the picture most.
        val y = 0.12f - 8f / 1920f
        val difference = lumas(glass, y).zip(lumas(clear, y)).map { (a, b) -> abs(a - b) }.average()
        Log.i("OnlyShots", "glass against blur, mean luma difference near the edge: $difference")
        assertTrue("glass should differ from the plain blur near its edge ($difference)", difference > 0.01)
        Log.i("OnlyShots", "barcode roughness: no panel $sharp, blur panel $blurred")
        assertTrue("no panel should leave the blocks sharp ($sharp)", sharp > 0.01f)
        assertTrue("blur $blurred should be well under sharp $sharp", blurred < sharp * 0.5f)
    }

    @Test
    fun aReframedExportZoomsAsThePreviewDoes() = runBlocking {
        val r = checkReframe(exporter, AndroidFrameSampler(), clip.absolutePath, target.cacheDir.toOkioPath())
        Log.i("OnlyShots", "Media3 reframe: $r")
        assertTrue("$r", r.mapped < 0.06 && r.mapped < r.unmapped * 0.5)
    }

    @Test
    fun everyFormatComesOutAtItsSize() = runBlocking {
        for (f in VideoFormat.entries) {
            val size = Quality.HD_1080.size(f)
            assertEquals(
                "$f",
                size.width to size.height,
                exportedSize(exporter, AndroidVideoProbe(), clip.absolutePath, target.cacheDir.toOkioPath(), f),
            )
        }
    }

    @Test
    fun aSquareAndALandscapeExportCropAsThePreviewDoes() = runBlocking {
        for (f in listOf(VideoFormat.SQUARE_1_1, VideoFormat.LANDSCAPE_16_9)) {
            val r = checkReframe(exporter, AndroidFrameSampler(), clip.absolutePath, target.cacheDir.toOkioPath(), f)
            Log.i("OnlyShots", "Media3 $f: $r")
            assertTrue("$f: $r", r.mapped < 0.06 && r.mapped < r.unmapped * 0.5)
        }
    }

    @Test
    fun aLandscapeCropCanMoveAlongThePortraitPicture() = runBlocking {
        val r =
            checkReframe(
                exporter,
                AndroidFrameSampler(),
                clip.absolutePath,
                target.cacheDir.toOkioPath(),
                VideoFormat.LANDSCAPE_16_9,
                SpikeLandscapeTop,
            )
        Log.i("OnlyShots", "Media3 pan: $r")
        assertTrue("$r", r.mapped < 0.06 && r.mapped < r.unmapped * 0.5)
    }

    @Test fun fullClipOverlayMatchesPicture() = export(0, SpikeClip.DURATION_US, listOf(5_000_000, 22_500_000, 40_000_000))

    @Test fun trimmedClipOverlayMatchesPicture() = export(2_000_000, 12_000_000, listOf(1_000_000, 5_000_000, 9_000_000))
}
