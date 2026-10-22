package app.onlyshots.platform

import androidx.compose.runtime.staticCompositionLocalOf
import okio.Path

/** What a picker is asked for. */
enum class PickKind {
    Video,
    Shot,
}

/**
 * Answers the app's pickers in place of the system's: UI tests provide one to hand back a test video or shot file, so a
 * flow that starts with "choose" runs without a dialog. Null (the system picker) everywhere in the app.
 */
fun interface PickerOverride {
    /** The file to hand back, or null for "cancelled". */
    fun pick(kind: PickKind): Path?
}

val LocalPickerOverride = staticCompositionLocalOf<PickerOverride?> { null }
