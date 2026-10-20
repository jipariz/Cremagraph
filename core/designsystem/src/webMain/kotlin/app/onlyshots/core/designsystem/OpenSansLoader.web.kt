package app.onlyshots.core.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as PlatformFont

internal actual fun fontFromBytes(identity: String, bytes: ByteArray, weight: FontWeight): Font =
    PlatformFont(identity, bytes, weight)
