package app.onlyshots.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import okio.Path

/**
 * Playback position as Compose state, so the overlay drawn on top follows every scrub frame without any extra plumbing.
 * The platform surface writes [positionUs] while playing and reads it after a [seek].
 */
@Stable
class PlayerState {
    var positionUs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)

    /** Silent playback, and a silent export: recordings always have sound, and this is where a take loses it. */
    var muted by mutableStateOf(false)
    internal var seekNonce by mutableIntStateOf(0)

    fun seek(us: Long) {
        positionUs = us
        seekNonce++
    }

    fun toggle() {
        playing = !playing
    }
}

/** ExoPlayer on Android, AVPlayer on iOS; both loop inside [clipUs]. */
@Composable
expect fun VideoSurface(
    file: Path,
    state: PlayerState,
    clipUs: LongRange,
    modifier: Modifier,
    /**
     * Draw the picture in Compose so Haze can blur it for a blur or glass style. Android's TextureView already is; iOS
     * switches from its player layer to the player's decoded frames.
     */
    drawFrames: Boolean = false,
)
