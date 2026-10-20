package app.onlyshots.platform

import androidx.compose.runtime.Composable

/**
 * Returns a function that asks for Bluetooth access when needed and then calls [onResult]. Android 12+ needs
 * BLUETOOTH_SCAN and BLUETOOTH_CONNECT at runtime (older versions need location); iOS asks by itself the first time
 * CoreBluetooth is used.
 */
@Composable expect fun rememberBluetoothPermission(onResult: (granted: Boolean) -> Unit): () -> Unit
