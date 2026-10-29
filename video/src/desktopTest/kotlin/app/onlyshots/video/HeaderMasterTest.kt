package app.onlyshots.video

import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.ShotFiles
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.overlay.compose.OverlayFonts
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

/**
 * Renders the 16:9 master for the App Store product page header (`design/header/`), through the app's own exporter so
 * the art is literally what the app makes. Not a test of anything: it is skipped unless it is handed a video, which is
 * why `./gradlew :video:desktopTest` stays green without one.
 *
 *     ./gradlew :video:desktopTest --tests '*HeaderMasterTest' \
 *       -Donlyshots.header.video=/path/IMG_7462.MOV \
 *       -Donlyshots.header.shot=/path/shot.json \
 *       -Donlyshots.header.out=/path/master.mp4 \
 *       -Donlyshots.header.shotStartUs=1000000 \
 *       -Donlyshots.header.durationUs=19498333
 *
 * The shot file is anything [ShotFiles.parse] reads — here a visualizer.coffee response. `shotStartUs` is where the
 * shot begins in the video, which the editor would work out from the two clocks; see design/header/README.md.
 */
class HeaderMasterTest {
    private fun prop(name: String): String? = System.getProperty("onlyshots.header.$name")?.takeIf { it.isNotBlank() }

    /** `x,y,w,h` as fractions of the frame. */
    private fun rect(): NormRect {
        val v = prop("rect")?.split(",")?.map { it.trim().toFloat() } ?: return NormRect(0.52f, 0.44f, 0.42f, 0.34f)
        require(v.size == 4) { "onlyshots.header.rect wants x,y,w,h" }
        return NormRect(v[0], v[1], v[2], v[3])
    }

    @Test
    fun rendersTheHeaderMaster() {
        val videoPath = prop("video") ?: return println("HeaderMasterTest: no onlyshots.header.video, skipping")
        val shotPath = checkNotNull(prop("shot")) { "onlyshots.header.shot is required with onlyshots.header.video" }
        val out = File(prop("out") ?: "${System.getProperty("java.io.tmpdir")}/header-master.mp4")
        val durationUs = checkNotNull(prop("durationUs")?.toLong()) { "onlyshots.header.durationUs is required" }
        val shotStartUs = prop("shotStartUs")?.toLong() ?: 0L

        val shot = ShotFiles.parse(File(shotPath).readText(), fallbackId = "header") { Instant.fromEpochSeconds(0) }
        val item =
            VideoItem(
                id = "header",
                file = File(videoPath).absolutePath,
                createdAt = Instant.fromEpochSeconds(0),
                durationUs = durationUs,
                origin = Origin.IMPORTED,
                hasAudio = true,
                shot = shot,
                shotStartInVideoUs = shotStartUs,
                syncLocked = false,
                trimInUs = 0L,
                trimOutUs = durationUs,
                // The header keeps a 1646 px band of a 4K 16:9 frame, so the default bottom-left placement would
                // lose its lower edge. The default here sits in the right half, clear of the furniture's copy, and
                // inside the band; `rect` moves it when the band does.
                overlay = OverlaySettings(styleId = prop("style") ?: "liquidglass", rect = rect()),
                format = VideoFormat.LANDSCAPE_16_9,
            )

        val exporter = FfmpegVideoExporter(videoPath = { File(it.file).toOkioPath() }, fonts = { OverlayFonts.Default })
        val last = runBlocking {
            exporter.export(item, Quality.UHD_4K, withSound = true, out = out.toOkioPath()).last()
        }
        assertTrue(last is ExportProgress.Done, "export did not finish: $last")
        println("HeaderMasterTest: ${out.absolutePath} ${out.length() / 1_000_000} MB")
    }
}
