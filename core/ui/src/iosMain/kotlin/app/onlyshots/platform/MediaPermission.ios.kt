package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
actual fun rememberMediaPermission(onResult: (camera: Boolean, mic: Boolean) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val camera = AVMediaTypeVideo?.let { granted(it) } ?: false
            val mic = AVMediaTypeAudio?.let { granted(it) } ?: false
            onResult(camera, mic)
        }
    }
}

private suspend fun granted(mediaType: String): Boolean =
    when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
        AVAuthorizationStatusAuthorized -> true
        AVAuthorizationStatusNotDetermined ->
            suspendCancellableCoroutine { cont ->
                AVCaptureDevice.requestAccessForMediaType(mediaType) { ok -> cont.resume(ok) }
            }
        else -> false
    }

@Composable
actual fun rememberCameraGranted(): () -> Boolean = {
    AVMediaTypeVideo?.let { AVCaptureDevice.authorizationStatusForMediaType(it) == AVAuthorizationStatusAuthorized }
        ?: false
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit = {
    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
        UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
