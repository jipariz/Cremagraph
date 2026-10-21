package app.onlyshots.edit

import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import okio.Path

@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(file: Path, state: PlayerState, clipUs: LongRange, modifier: Modifier, drawFrames: Boolean) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).setLoadControl(previewLoadControl()).build().apply {
            setMediaItem(MediaItem.fromUri("file://$file"))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, state.seekNonce) { player.seekTo(state.positionUs / 1000) }
    LaunchedEffect(player, state.playing) { player.playWhenReady = state.playing }
    LaunchedEffect(player, state.muted) { player.volume = if (state.muted) 0f else 1f }
    LaunchedEffect(player, clipUs) {
        while (true) {
            withFrameNanos { }
            if (!state.playing) continue
            val us = player.currentPosition * 1000
            if (us >= clipUs.last) {
                player.seekTo(clipUs.first / 1000)
                state.positionUs = clipUs.first
            } else {
                state.positionUs = us.coerceAtLeast(clipUs.first)
            }
        }
    }
    // A TextureView, not a SurfaceView: its pixels are part of the Compose layer, so a blur or glass style can blur the
    // video behind the graph (Haze cannot see into a separate surface). It stretches the video to its size, so a
    // transform crops it instead, as the exporters do: a 9:16 clip in a square frame shows its middle.
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { view ->
                player.setVideoTextureView(view)
                fun cover() = view.setTransform(coverMatrix(view.width, view.height, player.videoSize))
                player.addListener(
                    object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: VideoSize) = cover()
                    }
                )
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> cover() }
            }
        },
        modifier = modifier,
    )
}

/**
 * A few seconds ahead, at most [PREVIEW_BUFFER_BYTES]. The default buffers 50 s or about 130 MB of video in the Java
 * heap: a whole 4K take. The file is local, so a short buffer never stalls, and the export reads the same file with a
 * buffer of its own just as large: both together left a 256 MB heap out of memory a moment into a 4K render.
 */
@OptIn(UnstableApi::class)
private fun previewLoadControl() =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(PREVIEW_MIN_BUFFER_MS, PREVIEW_MAX_BUFFER_MS, PREVIEW_START_MS, PREVIEW_START_MS)
        .setTargetBufferBytes(PREVIEW_BUFFER_BYTES)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

private const val PREVIEW_MIN_BUFFER_MS = 2_000
private const val PREVIEW_MAX_BUFFER_MS = 5_000
private const val PREVIEW_START_MS = 500
private const val PREVIEW_BUFFER_BYTES = 16 * 1024 * 1024

/** Scales the stretched picture back to its own shape, covering the view and centred. */
private fun coverMatrix(viewW: Int, viewH: Int, video: VideoSize): Matrix {
    val m = Matrix()
    if (viewW <= 0 || viewH <= 0 || video.width <= 0 || video.height <= 0) return m
    val videoAspect = video.width * video.pixelWidthHeightRatio / video.height
    val viewAspect = viewW.toFloat() / viewH
    if (videoAspect > viewAspect) m.setScale(videoAspect / viewAspect, 1f, viewW / 2f, viewH / 2f)
    else m.setScale(1f, viewAspect / videoAspect, viewW / 2f, viewH / 2f)
    return m
}
