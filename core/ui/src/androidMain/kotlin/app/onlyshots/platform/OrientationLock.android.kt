package app.onlyshots.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Sensor portrait or sensor landscape: either way up, as the phone is held, but never the other shape. Android 16 and
 * later ignore it on a large screen (a tablet, a foldable's inner display); the record screen keeps a take's shape
 * there by not re-arming until the take ends.
 */
@Composable
actual fun LockOrientation(landscape: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, landscape) {
        activity.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
