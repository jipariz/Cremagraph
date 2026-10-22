package app.onlyshots.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

actual val exportsNotify: Boolean = true

@Composable
actual fun rememberNotificationPermission(onAnswer: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val answer by rememberUpdatedState(onAnswer)
    // Before Android 13 posting needs no permission, only the user not having turned the app's notifications off.
    // Outside an activity (a screenshot test) there is nobody to ask: no answer is a no.
    if (LocalActivityResultRegistryOwner.current == null) return { answer(false) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return { answer(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { answer(it) }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) answer(true) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
