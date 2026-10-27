package app.onlyshots.video

import android.graphics.BitmapFactory
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

    /**
     * The size asked for is the size that comes back, whether the decoder scaled it (API 27 and up) or the bitmap
     * was shrunk afterwards. The 1080-wide clip has to come down for either path to have done anything.
     */
    @Test fun aThumbnailIsNoWiderThanAsked() = runBlocking {
        val out = File(target.cacheDir, "thumb-108.jpg")
        probe.writeThumbnail(clip.toOkioPath(), atUs = 0, out = out.toOkioPath(), maxWidthPx = 108)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.toString(), bounds)
        assertTrue("thumbnail is ${bounds.outWidth} px wide", bounds.outWidth in 1..108)
        assertEquals(16f / 9f, bounds.outHeight.toFloat() / bounds.outWidth, 0.2f)
    }

    @Test fun decodesAudioTheOnsetDetectorCanRead() = runBlocking {
        val pcm = probe.decodeMonoPcm(clip.toOkioPath())
        assertNotNull(pcm)
        val onset = PumpOnsetDetector().detect(pcm!!.samples, pcm.sampleRate)
        assertNotNull("no pump onset found", onset)
        assertTrue("onset at $onset µs", abs(onset!! - 3_000_000) <= 150_000)
    }
}
