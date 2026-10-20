package app.onlyshots.layout

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable

@Composable actual fun platformWindowAdaptiveInfo(): WindowAdaptiveInfo = currentWindowAdaptiveInfo()
