package app.onlyshots.core.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.onlyshots.core.resources.Res
import app.onlyshots.overlay.compose.OverlayFonts
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** A font from raw bytes, usable outside composition. */
internal expect fun fontFromBytes(identity: String, bytes: ByteArray, weight: FontWeight): Font

/**
 * The overlay's typefaces for code that runs outside composition: the exporter's per-frame overlay renderer. Open Sans
 * ([openSans]) and Plex Mono ([plexMono]), the same files, read straight from the bundled resources.
 */
suspend fun loadOverlayFonts(): OverlayFonts =
    OverlayFonts(
        sans =
            family(
                "open_sans_light" to FontWeight.Light,
                "open_sans_regular" to FontWeight.Normal,
                "open_sans_semibold" to FontWeight.SemiBold,
            ),
        mono = family("plex_mono_regular" to FontWeight.Normal, "plex_mono_semibold" to FontWeight.SemiBold),
    )

@OptIn(ExperimentalResourceApi::class)
private suspend fun family(vararg files: Pair<String, FontWeight>): FontFamily =
    FontFamily(files.map { (name, weight) -> fontFromBytes(name, Res.readBytes("font/$name.ttf"), weight) })
