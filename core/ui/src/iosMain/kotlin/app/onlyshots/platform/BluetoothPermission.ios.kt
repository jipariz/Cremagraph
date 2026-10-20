package app.onlyshots.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberBluetoothPermission(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }
