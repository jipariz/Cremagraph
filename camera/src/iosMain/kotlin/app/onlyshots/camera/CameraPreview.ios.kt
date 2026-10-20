@file:OptIn(ExperimentalForeignApi::class)

package app.onlyshots.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView

/**
 * `UIKitView` resizes the view but not a `CALayer` inside it, so the preview layer is sized in `layoutSubviews` instead
 * of on recomposition.
 */
private class PreviewView(session: platform.AVFoundation.AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {
    val previewLayer =
        AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
            connection?.videoRotationAngle = interfaceRotationAngle()
        }

    init {
        layer.addSublayer(previewLayer)
    }

    /** The layer knows its aspect-fill crop and rotation, so it maps a point on screen to the sensor. */
    fun devicePoint(x: Float, y: Float): CValue<CGPoint> {
        val (w, h) = previewLayer.bounds.useContents { size.width to size.height }
        return previewLayer.captureDevicePointOfInterestForPoint(CGPointMake(x * w, y * h))
    }

    // A turned screen lays the view out again: the picture turns with it, upright either way.
    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
        previewLayer.connection?.videoRotationAngle = interfaceRotationAngle()
    }
}

@Composable
actual fun CameraPreview(controller: CameraController, modifier: Modifier, drawFrames: Boolean) {
    val ios = controller as? IosCameraController ?: return
    LaunchedEffect(ios) { ios.prepare() }
    DisposableEffect(ios, drawFrames) {
        if (drawFrames) ios.framesWanted++
        onDispose { if (drawFrames) ios.framesWanted-- }
    }
    if (drawFrames) {
        // The camera's own frames, drawn by Compose, so a blur or glass style can blur them.
        val frame by ios.previewFrame.collectAsState()
        Box(modifier.background(Color.Black)) {
            frame?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    } else {
        UIKitView(
            factory = { PreviewView(ios.session).also { view -> ios.focusPoint = view::devicePoint } },
            modifier = modifier,
            // Touches go to the record screen's own tap-to-focus and pinch-to-zoom layer, not to the view.
            properties = UIKitInteropProperties(interactionMode = null),
        )
    }
}
