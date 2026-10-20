package app.onlyshots.core.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/** Android fonts load from files; java.io.tmpdir is the app's cache directory. */
internal actual fun fontFromBytes(identity: String, bytes: ByteArray, weight: FontWeight): Font {
    val file = File(System.getProperty("java.io.tmpdir"), "onlyshots-font-$identity.ttf")
    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    return Font(file, weight)
}
