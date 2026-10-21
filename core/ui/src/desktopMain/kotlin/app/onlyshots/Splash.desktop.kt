package app.onlyshots

import androidx.compose.runtime.Composable

/** The desktop has no system-wide setting Compose can read. */
@Composable internal actual fun rememberReducedMotion(): Boolean = false
