package app.onlyshots.video

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

class FfmpegVideoProbeTest {
    private val clip = File(System.getProperty("onlyshots.spikeClip") ?: "testdata/${SpikeClip.NAME}").toOkioPath()
    private val probe = FfmpegVideoProbe()

    @Test
    fun readsTheSpikeClip() = runBlocking {
        val info = probe.info(clip)
        assertTrue(kotlin.math.abs(info.durationUs - SpikeClip.DURATION_US) < 100_000, "duration ${info.durationUs}")
        assertTrue(info.hasAudio)
        assertEquals(1920, info.heightPx)
    }

    @Test
    fun writesAThumbnailAndDecodesTheSound() = runBlocking {
        val out = File(System.getProperty("java.io.tmpdir"), "onlyshots-thumb.jpg").toOkioPath()
        probe.writeThumbnail(clip, 5_000_000, out, maxWidthPx = 270)
        val image = assertNotNull(ImageIO.read(out.toFile()))
        assertEquals(270, image.width)
        assertEquals(480, image.height)
        val pcm = assertNotNull(probe.decodeMonoPcm(clip, maxSeconds = 6))
        assertTrue(pcm.samples.size >= pcm.sampleRate * 5, "${pcm.samples.size} samples at ${pcm.sampleRate} Hz")
    }
}
