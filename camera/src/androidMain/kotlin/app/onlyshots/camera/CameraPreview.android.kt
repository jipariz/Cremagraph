package app.onlyshots.camera

import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun CameraPreview(controller: CameraController, modifier: Modifier, drawFrames: Boolean) {
    val android = controller as? AndroidCameraController ?: return
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(android, owner) { android.bind(owner) }
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // TextureView, not SurfaceView: the overlay and the controls draw over the
                // picture in the same window, and a screenshot shows what the user sees.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = android.cameraController
                android.previewView = this
                // Two screens can show the camera for a moment (the record screen and the style editor, while one
                // replaces the other). A PreviewView that leaves clears the controller's surface even when another
                // view has it by then, so the one still showing takes it back.
                addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}

                        override fun onViewDetachedFromWindow(v: View) {
                            val current = android.previewView ?: return
                            if (current === v) return
                            current.post {
                                if (current.isAttachedToWindow && android.previewView === current) {
                                    current.controller = null
                                    current.controller = android.cameraController
                                }
                            }
                        }
                    }
                )
            }
        },
        modifier = modifier,
    )
}
