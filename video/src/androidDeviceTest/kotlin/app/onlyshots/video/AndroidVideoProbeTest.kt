package app.onlyshots.video

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.onlyshots.core.model.PumpOnsetDetector
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The probe against the spike clip: 45 s, 1080 × 1920, 30 fps, pump tone from 3.0 s. */
@RunWith(AndroidJUnit4::class)
class AndroidVideoProbeTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val clip: File = File(target.cacheDir, SpikeClip.NAME).also { f ->
        InstrumentationRegistry.getInstrumentation().context.assets.open(SpikeClip.NAME).use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
    }
    private val probe = AndroidVideoProbe()

    @Test fun readsDurationAudioAndTheShownHeight() = runBlocking {
        val info = probe.info(clip.toOkioPath())
        assertEquals(45_000_000.0, info.durationUs.toDouble(), 100_000.0)
        assertTrue(info.hasAudio)
        assertEquals(1920, info.heightPx)
    }

    @Test fun writesAThumbnail() = runBlocking {
        val out = File(target.cacheDir, "thumb.jpg").toOkioPath()
        probe.writeThumbnail(clip.toOkioPath(), atUs = 0, out = out)
        assertTrue(File(out.toString()).length() > 0)
    }

    @Test fun decodesAudioTheOnsetDetectorCanRead() = runBlocking {
        val pcm = probe.decodeMonoPcm(clip.toOkioPath())
        assertNotNull(pcm)
        val onset = PumpOnsetDetector().detect(pcm!!.samples, pcm.sampleRate)
        assertNotNull("no pump onset found", onset)
        assertTrue("onset at $onset µs", abs(onset!! - 3_000_000) <= 150_000)
    }
}
