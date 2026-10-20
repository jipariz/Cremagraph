package app.onlyshots.video

import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ImportMatchTest {
    private fun shot(id: String, at: String) =
        ShotSummary(id, SourceKind.GAGGIUINO, id, Instant.parse(at), 30_000_000, 40f)

    private val shots =
        listOf(
            shot("early", "2026-10-11T08:00:00Z"),
            shot("match", "2026-10-11T08:14:10Z"),
            shot("late", "2026-10-11T09:00:00Z"),
        )

    @Test
    fun picksShotStartingInsideTheClip() {
        assertEquals("match", pickShotForImport(Instant.parse("2026-10-11T08:14:00Z"), 45_000_000, shots)?.id)
        // Creation time stamped at the end of the clip.
        assertEquals("match", pickShotForImport(Instant.parse("2026-10-11T08:14:50Z"), 45_000_000, shots)?.id)
    }

    @Test
    fun nothingNearby() {
        assertNull(pickShotForImport(Instant.parse("2026-10-11T12:00:00Z"), 45_000_000, shots))
    }

    @Test
    fun fourKOnlyWhenSourceAndEncoderAllow() {
        assertEquals(listOf(Quality.HD_1080), availableQualities(1920, encoderSupports4k = true))
        assertEquals(listOf(Quality.HD_1080), availableQualities(3840, encoderSupports4k = false))
        assertEquals(Quality.entries, availableQualities(3840, encoderSupports4k = true))
    }
}
