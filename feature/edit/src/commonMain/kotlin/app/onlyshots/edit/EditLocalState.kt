package app.onlyshots.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * What the editor keeps on screen only, never in the video: whether the graph is selected, whether the picture is being
 * framed, and the timeline's zoom.
 */
@Stable
class EditLocalState(initiallySelected: Boolean, initiallyReframing: Boolean) {
    var graphSelected by mutableStateOf(initiallySelected)

    var reframing by mutableStateOf(initiallyReframing)
        private set

    /** The timeline's zoom over the fitted strip; null fitted. */
    var zoom by mutableStateOf<Float?>(null)

    /** Framing puts the graph down: the picture moves under it, not the graph. */
    fun reframe(on: Boolean) {
        reframing = on
        if (on) graphSelected = false
    }

    fun toggleZoom() {
        zoom = nextZoom(zoom)
    }
}

@Composable
fun rememberEditLocalState(initiallySelected: Boolean = false, initiallyReframing: Boolean = false): EditLocalState =
    remember {
        EditLocalState(initiallySelected, initiallyReframing)
    }
