package app.onlyshots.video

import app.onlyshots.core.model.PumpOnsetDetector
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory

/** Same checks as the Android probe test; skipped when the clip path is not passed in. */
class AvFoundationVideoProbeTest {
    private val clip = NSProcessInfo.processInfo.environment["ONLYSHOTS_SPIKE_CLIP"] as? String
    private val probe = AvFoundationVideoProbe()

    @Test
    fun readsDurationAudioAndTheShownHeight() = runBlocking {
        val path =
            clip
                ?: run {
                    println("ONLYSHOTS_SPIKE_CLIP not set, skipping")
                    return@runBlocking
                }
        val info = probe.info(path.toPath())
        assertEquals(45.0, info.durationUs / 1_000_000.0, 0.1)
        assertTrue(info.hasAudio)
        assertEquals(1920, info.heightPx)
    }

    @Test
    fun decodesAudioTheOnsetDetectorCanRead() = runBlocking {
        val path =
            clip
                ?: run {
                    println("ONLYSHOTS_SPIKE_CLIP not set, skipping")
                    return@runBlocking
                }
        val out = (NSTemporaryDirectory() + "thumb.jpg").toPath()
        probe.writeThumbnail(path.toPath(), atUs = 0, out = out)
        assertTrue(okio.FileSystem.SYSTEM.metadata(out).size!! > 0)

        val pcm = probe.decodeMonoPcm(path.toPath())
        assertNotNull(pcm)
        val onset = PumpOnsetDetector().detect(pcm.samples, pcm.sampleRate)
        assertNotNull(onset, "no pump onset found")
        assertTrue(abs(onset - 3_000_000) <= 150_000, "onset at $onset µs")
    }
}
