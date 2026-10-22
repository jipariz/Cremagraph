package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual val exportsNotify: Boolean = true

/** Asks for alerts (the "saved" banner); iOS shows its prompt once and answers from then on by itself. */
@Composable
actual fun rememberNotificationPermission(onAnswer: (granted: Boolean) -> Unit): () -> Unit {
    val answer by rememberUpdatedState(onAnswer)
    return {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { granted, _ ->
            dispatch_async(dispatch_get_main_queue()) { answer(granted) }
        }
    }
}
