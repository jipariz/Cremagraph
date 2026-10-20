package app.onlyshots.core.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.onlyshots.core.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** A font from raw bytes, usable outside composition. */
internal expect fun fontFromBytes(identity: String, bytes: ByteArray, weight: FontWeight): Font

/**
 * Open Sans for code that runs outside composition: the exporter's per-frame overlay renderer. Same files as
 * [openSans], read straight from the bundled resources.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadOpenSans(): FontFamily {
    val files =
        listOf(
            "open_sans_light" to FontWeight.Light,
            "open_sans_regular" to FontWeight.Normal,
            "open_sans_semibold" to FontWeight.SemiBold,
        )
    return FontFamily(files.map { (name, weight) -> fontFromBytes(name, Res.readBytes("font/$name.ttf"), weight) })
}
