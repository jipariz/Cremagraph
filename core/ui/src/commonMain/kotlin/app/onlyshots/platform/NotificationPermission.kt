package app.onlyshots.platform

import androidx.compose.runtime.Composable

/**
 * Returns a function that asks, once, for permission to post the export notification. Nothing depends on the answer:
 * the export runs either way, and a refusal only means the progress is invisible outside the app. Already granted or
 * twice refused shows no UI, so it cannot nag.
 */
@Composable expect fun rememberNotificationPermission(): () -> Unit
