package app.onlyshots.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The desktop host has no camera: a flat dark frame stands in for the picture. */
@Composable
actual fun CameraPreview(controller: CameraController, modifier: Modifier, drawFrames: Boolean) {
    Box(modifier.background(Color(0xFF1A1512)))
}
