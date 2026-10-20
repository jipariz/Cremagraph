package app.onlyshots.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

val LocalCgTypography = staticCompositionLocalOf<CgTypography> { error("CgTheme missing") }
val LocalOverlayFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

object CgTheme {
    val type: CgTypography
        @Composable get() = LocalCgTypography.current

    /** Font family for the overlay renderer (Open Sans in every graph style). */
    val overlayFont: FontFamily
        @Composable get() = LocalOverlayFontFamily.current
}

/** Dark only: flat colour fields, rounded controls ([CgShape]), light display type. */
@Composable
fun CgTheme(content: @Composable () -> Unit) {
    val family = openSans()
    val typography = remember(family) { CgTypography.of(family) }
    val scheme =
        darkColorScheme(
            primary = CgColors.Text.Primary,
            onPrimary = CgColors.Text.OnLight,
            secondary = CgColors.Brand.Orange,
            background = CgColors.Surface.Base,
            onBackground = CgColors.Text.Primary,
            surface = CgColors.Surface.Bar,
            onSurface = CgColors.Text.Primary,
            surfaceVariant = CgColors.Surface.Raised,
            onSurfaceVariant = CgColors.Text.Secondary,
            outline = CgColors.Text.Dim,
            outlineVariant = CgColors.Line.Divider,
            error = CgColors.Status.Record,
            scrim = CgColors.Surface.Scrim,
        )
    CompositionLocalProvider(LocalCgTypography provides typography, LocalOverlayFontFamily provides family) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
