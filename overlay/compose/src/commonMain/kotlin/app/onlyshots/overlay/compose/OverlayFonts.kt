package app.onlyshots.overlay.compose

import androidx.compose.ui.text.font.FontFamily
import app.onlyshots.core.model.style.OverlayFont

/**
 * The overlay's two typefaces, loaded once (`loadOverlayFonts()` outside composition, `CgTheme.overlayFonts` in it).
 * Each style sets its text in one ([app.onlyshots.core.model.style.TextColors.font]); preview and export pick the same.
 */
class OverlayFonts(val sans: FontFamily, val mono: FontFamily) {
    fun of(font: OverlayFont): FontFamily =
        when (font) {
            OverlayFont.SANS -> sans
            OverlayFont.MONO -> mono
        }

    companion object {
        /** The platform's own faces, for tests that don't look at the letters. */
        val Default = OverlayFonts(FontFamily.Default, FontFamily.Monospace)
    }
}
