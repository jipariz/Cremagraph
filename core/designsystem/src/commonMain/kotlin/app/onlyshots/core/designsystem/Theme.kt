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
import app.onlyshots.overlay.compose.OverlayFonts

val LocalCgTypography = staticCompositionLocalOf<CgTypography> { error("CgTheme missing") }
val LocalOverlayFonts = staticCompositionLocalOf { OverlayFonts.Default }

object CgTheme {
    val type: CgTypography
        @Composable get() = LocalCgTypography.current

    /** The overlay renderer's typefaces: each graph style sets its text in one of them. */
    val overlayFonts: OverlayFonts
        @Composable get() = LocalOverlayFonts.current
}

/**
 * Dark only, the "Instrument" look: ink grounds, hairline edges, square corners ([CgShape]), Plex Mono for what names
 * and counts, Plex Sans for sentences. Material's own components (menus, sheets, fields) get the same type and corners.
 */
@Composable
fun CgTheme(content: @Composable () -> Unit) {
    val mono = plexMono()
    val sans = plexSans()
    val sansOverlay = openSans()
    val typography = remember(mono, sans) { CgTypography.of(mono, sans) }
    val overlay = remember(sansOverlay, mono) { OverlayFonts(sans = sansOverlay, mono = mono) }
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
    CompositionLocalProvider(LocalCgTypography provides typography, LocalOverlayFonts provides overlay) {
        MaterialTheme(colorScheme = scheme, typography = material, shapes = square, content = content)
    }
}
