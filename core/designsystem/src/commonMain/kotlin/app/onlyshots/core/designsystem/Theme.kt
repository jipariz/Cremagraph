package app.onlyshots.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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

/**
 * Dark only, the "Instrument" look: ink grounds, hairline edges, square corners ([CgShape]), Plex Mono for what names
 * and counts, Plex Sans for sentences. Material's own components (menus, sheets, fields) get the same type and corners.
 */
@Composable
fun CgTheme(content: @Composable () -> Unit) {
    val mono = plexMono()
    val sans = plexSans()
    val overlay = openSans()
    val typography = remember(mono, sans) { CgTypography.of(mono, sans) }
    val material =
        remember(typography) {
            Typography(
                bodyLarge = typography.body,
                bodyMedium = typography.bodySmall,
                bodySmall = typography.caption,
                labelLarge = typography.label,
                labelMedium = typography.labelStrong,
                labelSmall = typography.micro,
                titleLarge = typography.screenTitle,
                titleMedium = typography.cardTitle,
                titleSmall = typography.sheetTitle,
                headlineSmall = typography.pivot,
            )
        }
    val corner = RoundedCornerShape(CgRadius.R0)
    val square = Shapes(extraSmall = corner, small = corner, medium = corner, large = corner, extraLarge = corner)
    val scheme =
        darkColorScheme(
            primary = CgColors.Text.Primary,
            onPrimary = CgColors.Text.OnLight,
            secondary = CgColors.Brand.Signal,
            background = CgColors.Surface.Base,
            onBackground = CgColors.Text.Primary,
            surface = CgColors.Surface.Bar,
            onSurface = CgColors.Text.Primary,
            surfaceVariant = CgColors.Surface.Raised,
            onSurfaceVariant = CgColors.Text.Secondary,
            surfaceContainer = CgColors.Surface.Bar,
            outline = CgColors.Line.Hairline,
            outlineVariant = CgColors.Line.Divider,
            error = CgColors.Status.Record,
            scrim = CgColors.Surface.Scrim,
        )
    CompositionLocalProvider(LocalCgTypography provides typography, LocalOverlayFontFamily provides overlay) {
        MaterialTheme(colorScheme = scheme, typography = material, shapes = square, content = content)
    }
}
