package app.onlyshots.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The live picture, cropped to fill its bounds. Put it behind the overlay in the same 9:16 box so what the user frames
 * is what gets recorded.
 */
@Composable
expect fun CameraPreview(
    controller: CameraController,
    modifier: Modifier,
    /**
     * Draw the picture in Compose rather than in a native layer, so Haze can blur it. Android's TextureView preview is
     * already part of the Compose layer; iOS switches from its preview layer to the camera's own frames.
     */
    drawFrames: Boolean = false,
)
