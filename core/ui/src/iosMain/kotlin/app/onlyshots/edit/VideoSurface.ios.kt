@file:OptIn(ExperimentalForeignApi::class)

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitView
import app.onlyshots.camera.PixelBuffers
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import okio.Path
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.addOutput
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeOutput
import platform.AVFoundation.seekToTime
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSURL
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIView

private class PlayerView(player: AVPlayer) : UIView(frame = CGRectZero.readValue()) {
    val playerLayer =
        AVPlayerLayer().apply {
            this.player = player
            // Cover, as the exporters crop: what is outside the frame is outside the video.
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }

    init {
        layer.addSublayer(playerLayer)
    }

    // UIKitView resizes the view, not the layer inside it.
    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.setFrame(bounds)
    }
}

@Composable
actual fun VideoSurface(file: Path, state: PlayerState, clipUs: LongRange, modifier: Modifier, drawFrames: Boolean) {
    val player = remember(file) { AVPlayer(uRL = NSURL.fileURLWithPath(file.toString())) }
    // Decoded frames for a blur or glass style: the player layer is native, so Haze cannot blur it.
    val output =
        remember(player) {
            AVPlayerItemVideoOutput(
                pixelBufferAttributes = mapOf<Any?, Any?>(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA)
            )
        }
    DisposableEffect(player, drawFrames) {
        val item = player.currentItem
        if (drawFrames) item?.addOutput(output)
        onDispose { if (drawFrames) item?.removeOutput(output) }
    }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    if (drawFrames) {
        LaunchedEffect(player, output) {
            while (true) {
                withFrameNanos {}
                val t = output.itemTimeForHostTime(CACurrentMediaTime())
                if (output.hasNewPixelBufferForItemTime(t)) {
                    output.copyPixelBufferForItemTime(t, null)?.let { pb ->
                        frame = PixelBuffers.toImageBitmap(pb, FRAME_WIDTH)
                        CVPixelBufferRelease(pb)
                    }
                }
            }
        }
    }
    DisposableEffect(player) { onDispose { player.pause() } }
    LaunchedEffect(player, state.seekNonce) { player.seekToTime(CMTimeMake(state.positionUs, 1_000_000)) }
    LaunchedEffect(player, state.playing) { if (state.playing) player.play() else player.pause() }
    LaunchedEffect(player, state.muted) { player.muted = state.muted }
    LaunchedEffect(player, clipUs) {
        while (true) {
            withFrameNanos {}
            if (!state.playing) continue
            val us = (CMTimeGetSeconds(player.currentTime()) * 1_000_000).toLong()
            if (us >= clipUs.last) {
                player.seekToTime(CMTimeMake(clipUs.first, 1_000_000))
                state.positionUs = clipUs.first
            } else {
                state.positionUs = us.coerceAtLeast(clipUs.first)
            }
        }
    }
    if (drawFrames) {
        Box(modifier.background(Color.Black)) {
            frame?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    } else {
        UIKitView(factory = { PlayerView(player) }, modifier = modifier)
    }
}

private const val FRAME_WIDTH = 540
