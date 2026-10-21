package app.onlyshots.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.open_sans_light
import app.onlyshots.core.resources.open_sans_regular
import app.onlyshots.core.resources.open_sans_semibold
import app.onlyshots.core.resources.plex_mono_regular
import app.onlyshots.core.resources.plex_mono_semibold
import app.onlyshots.core.resources.plex_sans_regular
import app.onlyshots.core.resources.plex_sans_semibold
import org.jetbrains.compose.resources.Font

/** Open Sans in the three weights the overlay uses: the graph's own type in most styles, on screen and in exports. */
@Composable
fun openSans(): FontFamily {
    val light = Font(Res.font.open_sans_light, FontWeight.Light)
    val regular = Font(Res.font.open_sans_regular, FontWeight.Normal)
    val semibold = Font(Res.font.open_sans_semibold, FontWeight.SemiBold)
    return remember(light, regular, semibold) { FontFamily(light, regular, semibold) }
}

/** IBM Plex Mono: the app's labels, titles and numbers. */
@Composable
fun plexMono(): FontFamily {
    val regular = Font(Res.font.plex_mono_regular, FontWeight.Normal)
    val semibold = Font(Res.font.plex_mono_semibold, FontWeight.SemiBold)
    return remember(regular, semibold) { FontFamily(regular, semibold) }
}

/** IBM Plex Sans: the app's sentences. */
@Composable
fun plexSans(): FontFamily {
    val regular = Font(Res.font.plex_sans_regular, FontWeight.Normal)
    val semibold = Font(Res.font.plex_sans_semibold, FontWeight.SemiBold)
    return remember(regular, semibold) { FontFamily(regular, semibold) }
}

/**
 * A label as the Instrument look sets it: capitals. The strings stay in sentence case in the XML (they are read aloud,
 * and other screens use them as sentences); only the drawing is upper case.
 */
fun String.caps(): String = uppercase()

/**
 * The text styles. Mono SemiBold names and counts things (titles, labels, buttons, values), tracked out a little; Sans
 * Regular says sentences.
 */
@Immutable
data class CgTypography(
    /** The one headline of a screen ("Your shot, with its graph"). */
    val pivot: TextStyle,
    val screenTitle: TextStyle,
    val sheetTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    /** Buttons, row names, top bars: Mono 15. */
    val label: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
    val valueReading: TextStyle,
    val valueLarge: TextStyle,
    /** Section headers and small buttons: Mono 12, tracked wide. */
    val labelStrong: TextStyle,
    /** Addresses, times, units beside a value: Mono 12 Regular. */
    val data: TextStyle,
) {
    companion object {
        fun of(mono: FontFamily, sans: FontFamily): CgTypography {
            fun m(size: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = 0.0) =
                TextStyle(
                    fontFamily = mono,
                    fontSize = size.sp,
                    fontWeight = weight,
                    letterSpacing = tracking.em,
                    color = CgColors.Text.Primary,
                )
            fun s(size: Int, weight: FontWeight = FontWeight.Normal) =
                TextStyle(fontFamily = sans, fontSize = size.sp, fontWeight = weight, color = CgColors.Text.Primary)
            return CgTypography(
                pivot = m(28, tracking = -0.02).copy(lineHeight = (28 * 1.15).sp),
                screenTitle = m(20, tracking = 0.04),
                sheetTitle = m(15, tracking = 0.06),
                cardTitle = m(18),
                body = s(15),
                bodySmall = s(13),
                label = m(15, tracking = 0.04),
                caption = s(12),
                micro = m(10, FontWeight.Normal),
                valueReading = m(20),
                valueLarge = m(40),
                labelStrong = m(12, tracking = 0.08),
                data = m(12, FontWeight.Normal),
            )
        }
    }
}
