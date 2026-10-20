package app.onlyshots.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.onlyshots.video.WebMedia
import kotlinx.coroutines.isActive
import okio.Path

/**
 * The web editor's picture: a hidden `<video>` keeps the clock and plays the sound, and each new frame is copied into
 * Compose and drawn crop-filled, so Haze can blur it for a blur or glass style as on the phones. Playing, it loops
 * inside [clipUs]; paused, it shows the frame under the playhead after every seek.
 */
@Composable
actual fun VideoSurface(file: Path, state: PlayerState, clipUs: LongRange, modifier: Modifier, drawFrames: Boolean) {
    val id = remember(file) { WebMedia.playerOpen(file.toString(), MAX_SIDE) }
    DisposableEffect(id) { onDispose { WebMedia.playerClose(id) } }
    var frame by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    fun grab(): ImageBitmap? = WebMedia.playerFrame(id)?.asComposeImageBitmap()
    LaunchedEffect(id, state.muted) { WebMedia.playerMute(id, state.muted) }
    LaunchedEffect(id, state.playing, state.seekNonce, clipUs) {
        if (!state.playing) {
            WebMedia.playerPause(id)
            WebMedia.playerSeek(id, state.positionUs / 1_000_000.0)
            // The seeked frame reaches the video a moment after the seek resolves, later still while the file loads.
            while (isActive) {
                withFrameNanos {}
                grab()?.let {
                    frame = it
                    return@LaunchedEffect
                }
            }
            return@LaunchedEffect
        }
        val from = state.positionUs.takeIf { it in clipUs && it < clipUs.last - END_SLACK_US } ?: clipUs.first
        WebMedia.playerSeek(id, from / 1_000_000.0)
        WebMedia.playerPlay(id)
        try {
            while (isActive) {
                withFrameNanos {}
                val atUs = (WebMedia.playerTime(id) * 1_000_000).toLong()
                if (atUs >= clipUs.last) {
                    WebMedia.playerSeek(id, clipUs.first / 1_000_000.0)
                    continue
                }
                grab()?.let { frame = it }
                state.positionUs = atUs
            }
        } finally {
            WebMedia.playerPause(id)
        }
    }
    Box(modifier.background(Color(0xFF1A1512))) {
        frame?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Preview frames are at most this many pixels on the long side: sharp in the editor, cheap to copy. */
private const val MAX_SIDE = 1280

private const val END_SLACK_US = 50_000L
