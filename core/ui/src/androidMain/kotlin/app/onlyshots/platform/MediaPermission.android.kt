package app.onlyshots.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

@Composable
actual fun rememberMediaPermission(onResult: (camera: Boolean, mic: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Only the missing ones were asked for; the others were already granted.
        val camera = result[Manifest.permission.CAMERA] ?: has(Manifest.permission.CAMERA)
        val mic = result[Manifest.permission.RECORD_AUDIO] ?: has(Manifest.permission.RECORD_AUDIO)
        onResult(camera, mic)
    }
    return {
        val missing = permissions.filterNot(::has)
        if (missing.isEmpty()) onResult(true, true) else launcher.launch(missing.toTypedArray())
    }
}

@Composable
actual fun rememberCameraGranted(): () -> Boolean {
    val context = LocalContext.current
    return {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
