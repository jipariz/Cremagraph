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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.onlyshots.video.FfmpegPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path

/**
 * The desktop editor's picture: FFmpeg decodes the file ([FfmpegPlayer]) and Compose draws each frame, crop-filled, so
 * Haze can blur it for a blur or glass style as on the phones. While playing it runs in real time with sound (unless
 * muted), looping inside [clipUs]; paused, it shows the frame under the playhead after every seek.
 */
@Composable
actual fun VideoSurface(file: Path, state: PlayerState, clipUs: LongRange, modifier: Modifier, drawFrames: Boolean) {
    val player = remember(file) { FfmpegPlayer(file) }
    DisposableEffect(player) { onDispose { player.close() } }
    var frame by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(player, state.playing, state.seekNonce) {
        if (!state.playing) player.frameAt(state.positionUs)?.let { frame = it }
    }
    LaunchedEffect(player, state.playing, state.seekNonce, clipUs) {
        if (!state.playing) return@LaunchedEffect
        player.play(state.positionUs, clipUs, muted = { state.muted }) { picture, atUs ->
            withContext(Dispatchers.Main) {
                frame = picture
                state.positionUs = atUs
            }
        }
    }
    Box(modifier.background(Color(0xFF1A1512))) {
        frame?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
