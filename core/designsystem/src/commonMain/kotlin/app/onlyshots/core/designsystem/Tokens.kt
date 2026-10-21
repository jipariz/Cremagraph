package app.onlyshots.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The "Instrument" palette (dark only): near-black ink, bone text, one signal accent, hairlines. The canvas "OnlyShots
 * · Instrument" is the reference.
 */
object CgColors {
    object Surface {
        /** Ink: every screen's ground. */
        val Base = Color(0xFF0B0B0B)
        /** Bars, sheets, cards: one step up from [Base]. */
        val Bar = Color(0xFF111110)
        /** Pressed and selected cells, fields, notices. */
        val Raised = Color(0xFF1A1A19)
        val Scrim = Color(0xFF000000)
    }

    object Text {
        /** Bone: 15.6 : 1 on Base. */
        val Primary = Color(0xFFE8E6E1)
        /** Secondary lines: 7.6 : 1 on Base, 6.7 : 1 on Raised. */
        val Secondary = Color(0xFFA3A19B)
        /** Hints, units, timestamps. As [Secondary]: the Instrument look keeps one quiet grey, readable everywhere. */
        val Dim = Color(0xFFA3A19B)
        /** What can't be pressed yet, and nothing that must be read. */
        val Disabled = Color(0xFF6E6C67)
        val OnLight = Color(0xFF0B0B0B)
    }

    object Line {
        /** Rules inside a panel, the graph's grid. */
        val Divider = Color(0xFF2A2A28)

        /** The 1 dp edge of every cell, field, button and panel. */
        val Hairline = Color(0xFF3A3A38)
    }

    object Status {
        /** Recording only. White on it holds 4.9 : 1. */
        val Record = Color(0xFFDC1B34)
        val RecordingChip = Color(0xFFA20025)
        /** Connected, saved. Ink on it, not white. */
        val Success = Color(0xFF5CD08A)
        /** Needs a look. Ink on it, not white. */
        val Warning = Color(0xFFFFB020)
        val Info = Color(0xFF1F6FB8)
        val Live = Color(0xFF5CD08A)
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
        /** The signal: the mark's tile, the one primary action on a screen, the selection. Ink on it (6.6 : 1). */
        val Signal = Color(0xFFFF5A1F)
        /** The mark's ink and the light fill of a selected segment. */
        val Bone = Color(0xFFE8E6E1)
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

/** Line weights: every edge is a hairline; the signal rule tops sheets and dialogs. */
object CgStroke {
    val Hairline = 1.dp
    val Rule = 2.dp
}

/** Instrument corners are square: every radius is 0. The names stay so each use says what it is. */
object CgRadius {
    val R0 = 0.dp
    val R8 = 0.dp
    val R12 = 0.dp
    /** Chips, colour chips, the toolbar buttons. */
    val Chip = 0.dp
    /** The top corners of every bottom sheet. */
    val Sheet = 0.dp
    val Dialog = 0.dp
    val Field = 0.dp
}

/** The shapes every component shares, so a segment, a button and a switch are cut the same way: square. */
object CgShape {
    /** Buttons, segments, switches, steppers. */
    val Pill: Shape = RectangleShape
    val Chip: Shape = RectangleShape
    val Sheet: Shape = RectangleShape
    val Dialog: Shape = RectangleShape
    val Field: Shape = RectangleShape
}

object CgSize {
    /** Minimum touch target. */
    val Touch = 44.dp
    val Record = 70.dp
}
