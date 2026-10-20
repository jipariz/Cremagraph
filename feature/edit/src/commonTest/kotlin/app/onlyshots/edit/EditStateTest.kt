package app.onlyshots.edit

import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EditStateTest {
    private fun item(syncLocked: Boolean = false) =
        VideoItem(
            id = "v",
            file = "v.mp4",
            createdAt = Instant.fromEpochSeconds(0),
            durationUs = 40_000_000,
            origin = if (syncLocked) Origin.RECORDED else Origin.IMPORTED,
            hasAudio = false,
            shot = null,
            shotStartInVideoUs = 5_000_000,
            syncLocked = syncLocked,
            trimInUs = 0,
            trimOutUs = 40_000_000,
            overlay = OverlaySettings(),
        )

    @Test
    fun trimNeverGoesBelowThreeSeconds() {
        val state = EditState(item())
        state.change { it.trimmed(inUs = 39_000_000, outUs = 40_000_000) }
        assertEquals(VideoItem.MIN_TRIM_US, state.item.trimmedDurationUs)
    }

    @Test
    fun aLockedFlagDoesNotMoveAndDoesNotCostAnUndoStep() {
        val state = EditState(item(syncLocked = true))
        state.change { it.withShotStart(12_000_000) }
        assertEquals(5_000_000, state.item.shotStartInVideoUs)
        assertFalse(state.canUndo)
    }

    @Test
    fun undoGoesBackOneStepAtATime() {
        val state = EditState(item())
        state.change { it.trimmed(1_000_000, it.trimOutUs) }
        state.change { it.withShotStart(8_000_000) }
        state.undo()
        assertEquals(5_000_000, state.item.shotStartInVideoUs)
        assertEquals(1_000_000, state.item.trimInUs)
        state.undo()
        assertEquals(0, state.item.trimInUs)
        assertFalse(state.canUndo)
    }

    @Test
    fun oneDragIsOneUndoStep() {
        val state = EditState(item())
        state.change(push = true) { it.trimmed(1_000_000, it.trimOutUs) }
        // …the rest of the drag updates without pushing.
        for (us in 2_000_000L..6_000_000L step 1_000_000) state.change(push = false) { it.trimmed(us, it.trimOutUs) }
        assertEquals(6_000_000, state.item.trimInUs)
        assertTrue(state.canUndo)
        state.undo()
        assertEquals(0, state.item.trimInUs)
        assertFalse(state.canUndo)
    }

    @Test
    fun redoBringsBackWhatUndoTookAndANewEditDropsIt() {
        val state = EditState(item())
        state.change { it.trimmed(inUs = 2_000_000, outUs = 40_000_000) }
        state.change { it.trimmed(inUs = 4_000_000, outUs = 40_000_000) }
        state.undo()
        assertTrue(state.canRedo)
        assertEquals(2_000_000, state.item.trimInUs)
        state.redo()
        assertEquals(4_000_000, state.item.trimInUs)
        assertFalse(state.canRedo)
        state.undo()
        state.change { it.trimmed(inUs = 1_000_000, outUs = 40_000_000) }
        assertFalse(state.canRedo)
        state.undo()
        assertEquals(2_000_000, state.item.trimInUs)
    }
}
