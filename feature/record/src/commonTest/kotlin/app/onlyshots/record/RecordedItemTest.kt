package app.onlyshots.record

import app.onlyshots.camera.Recording
import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordedItemTest {
    private val shot =
        Shot(
            id = "s",
            source = SourceKind.GAGGIUINO,
            profileName = "Zer0",
            startedAt = Instant.fromEpochSeconds(0),
            durationUs = 30_000_000,
            finalWeight = 36f,
            samples = emptyList(),
            phaseStartsUs = emptyList(),
        )

    private fun item(recording: Recording, shot: Shot? = this.shot, preRoll: Int = 3) =
        recordedVideoItem(
            id = "v",
            file = "v.mp4",
            createdAt = Instant.fromEpochSeconds(100),
            recording = recording,
            finished = FinishedRecording(shot, recording.durationUs, recording.triggerVideoUs, null),
            preRollSeconds = preRoll,
            overlay = OverlaySettings(),
        )

    @Test
    fun theHeadInFrontOfTheProRollIsTrimmedOff() {
        // Armed 4.2 s before the shot started, 3 s of that is the wanted pre-roll.
        val v = item(Recording("f", durationUs = 40_000_000, hasAudio = false, triggerVideoUs = 4_200_000))
        assertEquals(1_200_000, v.trimInUs)
        assertEquals(4_200_000, v.shotStartInVideoUs)
        assertEquals(40_000_000, v.trimOutUs)
        assertTrue(v.syncLocked)
        assertEquals(Origin.RECORDED, v.origin)
    }

    @Test
    fun aShortHeadIsKeptWholeRatherThanTrimmedPastTheStart() {
        // The camera restarted its file just before the trigger: only 0.8 s of pre-roll exists.
        val v = item(Recording("f", durationUs = 30_000_000, hasAudio = true, triggerVideoUs = 800_000))
        assertEquals(0, v.trimInUs)
        assertEquals(800_000, v.shotStartInVideoUs)
        assertTrue(v.hasAudio)
    }

    @Test
    fun aManualRecordingWithoutAShotIsNotLocked() {
        val v = item(Recording("f", durationUs = 12_000_000, hasAudio = false, triggerVideoUs = null), shot = null)
        assertEquals(0, v.trimInUs)
        assertEquals(null, v.shotStartInVideoUs)
        assertFalse(v.syncLocked)
        assertFalse(v.isLinked)
    }
}
