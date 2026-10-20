package app.onlyshots.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Figma variable collection "OnlyShots" (dark mode only). Names follow the Figma groups. */
object CgColors {
    object Surface {
        val Base = Color(0xFF120D0B)
        val Bar = Color(0xFF1A1412)
        val Raised = Color(0xFF2E2421)
        val Scrim = Color(0xFF000000)
    }

    object Text {
        val Primary = Color(0xFFFFFFFF)
        val Secondary = Color(0xFFC9BEBA)
        /** Hints, units, timestamps: 4.75 : 1 on Raised, more on Bar and Base. */
        val Dim = Color(0xFF9A8E8A)
        val OnLight = Color(0xFF120D0B)
    }

    object Line {
        val Divider = Color(0xFF2E2421)

        /** The 1 dp edge of a raised row or card, so it stands off Base and Bar. */
        val Hairline = Color(0xFF3A2F2B)
    }

    object Status {
        /** Recording only. White on it holds 4.9 : 1. */
        val Record = Color(0xFFDC1B34)
        val RecordingChip = Color(0xFFA20025)
        val Success = Color(0xFF008A00)
        val Warning = Color(0xFF8A5E00)
        val Info = Color(0xFF1F6FB8)
        val Live = Color(0xFF5CF08A)
        val Highlight = Color(0xFFFFC94D)
    }

    object Machine {
        val Gaggiuino = Color(0xFFC8461C)
        val Gaggimate = Color(0xFF00807E)
        val Decent = Color(0xFF2F4A6A)
        val Scale = Color(0xFF5B3FA8)
    }

    /** Series colours of the Tiles style, also used for the series chips. */
    object Series {
        val Pressure = Color(0xFF6FA8FF)
        val Flow = Color(0xFFFFC94D)
        val Weight = Color(0xFFE8E0DC)
        val WeightFlow = Color(0xFF5CC4B8)
        val Temperature = Color(0xFFFF8A5C)
    }

    object Brand {
        /** The mark, export and save, nothing else. Labels on it are [Text.OnLight] (5.3 : 1); white is too faint. */
        val Orange = Color(0xFFEF4E2B)
        /** The mark's frame on dark grounds. */
        val Cream = Color(0xFFF4EEEA)
    }
}

object CgSpace {
    val S4 = 4.dp
    val S6 = 6.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S14 = 14.dp
    val S16 = 16.dp
    val S24 = 24.dp
}

object CgRadius {
    val R0 = 0.dp
    val R8 = 8.dp
    val R12 = 12.dp
    /** Chips, colour chips, the toolbar buttons. */
    val Chip = 16.dp
    /** The top corners of every bottom sheet. */
    val Sheet = 16.dp
    val Dialog = 20.dp
    val Field = 12.dp
}

/** The rounded shapes every component shares, so a segment, a button and a switch round the same way. */
object CgShape {
    /** Fully round ends: buttons, segments, switches, steppers. */
    val Pill = RoundedCornerShape(percent = 50)
    val Chip = RoundedCornerShape(CgRadius.Chip)
    val Sheet = RoundedCornerShape(topStart = CgRadius.Sheet, topEnd = CgRadius.Sheet)
    val Dialog = RoundedCornerShape(CgRadius.Dialog)
    val Field = RoundedCornerShape(CgRadius.Field)
}

object CgSize {
    /** Minimum touch target. */
    val Touch = 44.dp
    val Record = 70.dp
}
