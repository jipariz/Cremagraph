package app.onlyshots.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.open_sans_light
import app.onlyshots.core.resources.open_sans_regular
import app.onlyshots.core.resources.open_sans_semibold
import org.jetbrains.compose.resources.Font

/** Open Sans in the three weights the app uses. The overlay renderer uses the same family. */
@Composable
fun openSans(): FontFamily {
    val light = Font(Res.font.open_sans_light, FontWeight.Light)
    val regular = Font(Res.font.open_sans_regular, FontWeight.Normal)
    val semibold = Font(Res.font.open_sans_semibold, FontWeight.SemiBold)
    return remember(light, regular, semibold) { FontFamily(light, regular, semibold) }
}

/** Figma text styles. */
@Immutable
data class CgTypography(
    val pivot: TextStyle,
    val screenTitle: TextStyle,
    val sheetTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
    val valueReading: TextStyle,
    val valueLarge: TextStyle,
    val labelStrong: TextStyle,
) {
    companion object {
        fun of(family: FontFamily): CgTypography {
            fun s(size: Int, weight: FontWeight = FontWeight.Normal) =
                TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, color = CgColors.Text.Primary)
            return CgTypography(
                pivot = s(34, FontWeight.Light),
                screenTitle = s(30, FontWeight.Light),
                sheetTitle = s(26, FontWeight.Light),
                cardTitle = s(20, FontWeight.Light),
                body = s(16),
                bodySmall = s(14),
                label = s(13),
                caption = s(12),
                micro = s(10),
                valueReading = s(20, FontWeight.Light),
                valueLarge = s(40, FontWeight.Light),
                labelStrong = s(12, FontWeight.SemiBold),
            )
        }
    }
}
