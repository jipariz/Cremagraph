package app.onlyshots.platform

import androidx.compose.runtime.Composable

/**
 * Returns a function that asks for the camera and the microphone together and then calls [onResult] with each answer. A
 * refused microphone only means silent recordings; a refused camera means none.
 */
@Composable expect fun rememberMediaPermission(onResult: (camera: Boolean, mic: Boolean) -> Unit): () -> Unit

/** Returns a check, without asking, of whether the camera may be used now: after a trip to the system settings. */
@Composable expect fun rememberCameraGranted(): () -> Boolean

/** Returns a function that opens this app's page in the system settings, where a refused permission is given back. */
@Composable expect fun rememberOpenAppSettings(): () -> Unit

/** Keeps the screen on while the app is armed or recording. */
@Composable expect fun KeepScreenOn(enabled: Boolean)
