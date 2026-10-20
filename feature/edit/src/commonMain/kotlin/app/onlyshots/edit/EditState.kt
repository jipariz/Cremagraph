package app.onlyshots.edit

import app.onlyshots.core.model.VideoItem

/**
 * The video being edited plus its undo and redo stacks. Every rule lives on [VideoItem] already — `trimmed` keeps three
 * seconds, `withShotStart` is a no-op on a synced recording — so this only decides what counts as one step.
 *
 * The stack is in memory: edits are undoable until the screen is left, which is what the spec asks for.
 */
class EditState(initial: VideoItem) {
    var item: VideoItem = initial
        private set

    private val undoStack = ArrayDeque<VideoItem>()
    private val redoStack = ArrayDeque<VideoItem>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    /** @param push false while a drag is in flight, so one gesture is one undo step rather than one per pixel. */
    fun change(push: Boolean = true, edit: (VideoItem) -> VideoItem) {
        val next = edit(item)
        if (next == item) return
        // A new edit is a new future: what was undone can't be redone onto it.
        redoStack.clear()
        if (push) {
            undoStack.addLast(item)
            while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        }
        item = next
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(item)
        item = previous
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(item)
        item = next
    }

    private companion object {
        const val MAX_UNDO = 50
    }
}
