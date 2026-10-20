package app.onlyshots.platform

import androidx.compose.runtime.Composable

/** iOS exports run in the foreground and post nothing, so there is nothing to ask for. */
@Composable actual fun rememberNotificationPermission(): () -> Unit = {}
