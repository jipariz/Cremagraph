package app.onlyshots.platform

import androidx.compose.runtime.Composable

/**
 * Whether an export is shown outside the app here (a notification on the phones, the tab and a notification on the
 * web), so the export screen offers to tell the user when it is done. The desktop app shows nothing.
 */
expect val exportsNotify: Boolean

/**
 * Returns a function that asks for permission to post the export's notifications, then calls [onAnswer] with whether
 * they may be posted. Nothing depends on the answer: the export runs either way, and a refusal only means it is not
 * seen outside the app. Already granted, or refused for good, shows no UI.
 */
@Composable expect fun rememberNotificationPermission(onAnswer: (granted: Boolean) -> Unit = {}): () -> Unit
