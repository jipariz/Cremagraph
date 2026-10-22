package app.onlyshots.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.ic_arrow_right
import app.onlyshots.core.resources.ic_bell
import app.onlyshots.core.resources.ic_bluetooth
import app.onlyshots.core.resources.ic_caret_down
import app.onlyshots.core.resources.ic_check
import app.onlyshots.core.resources.ic_chevron_down
import app.onlyshots.core.resources.ic_chevron_left
import app.onlyshots.core.resources.ic_chevron_right
import app.onlyshots.core.resources.ic_close
import app.onlyshots.core.resources.ic_contrast
import app.onlyshots.core.resources.ic_edit
import app.onlyshots.core.resources.ic_file
import app.onlyshots.core.resources.ic_film
import app.onlyshots.core.resources.ic_flag
import app.onlyshots.core.resources.ic_frame
import app.onlyshots.core.resources.ic_gallery
import app.onlyshots.core.resources.ic_help
import app.onlyshots.core.resources.ic_launch
import app.onlyshots.core.resources.ic_lock
import app.onlyshots.core.resources.ic_machine
import app.onlyshots.core.resources.ic_minus
import app.onlyshots.core.resources.ic_onlyshots
import app.onlyshots.core.resources.ic_pause
import app.onlyshots.core.resources.ic_play
import app.onlyshots.core.resources.ic_plus
import app.onlyshots.core.resources.ic_record
import app.onlyshots.core.resources.ic_settings
import app.onlyshots.core.resources.ic_share
import app.onlyshots.core.resources.ic_shield
import app.onlyshots.core.resources.ic_stop
import org.jetbrains.compose.resources.vectorResource

/**
 * The app's own icons (design/icons/GUIDELINES.md: a 24 grid, one 1.5 square-capped stroke, right angles, filled
 * squares for accents), written as vector drawables in :core:resources (`drawable/ic_*.xml`) by design/icons/build.py.
 * They are drawn in white and tinted where they are used; never a character in place of an icon.
 */
object CgIcons {
    /**
     * The Signal tile in its small cut (design/logo/kit/onlyshots-symbol-small.svg), the weight the logo guidelines set
     * for 16 to 47 px: a signal square with the shot's trace cut in from its left edge to a square pen head on a stem.
     */
    val OnlyShots: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_onlyshots)

    /** Back, and a frame back on the timeline. */
    val ChevronLeft: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_chevron_left)

    /** A frame on; a row or card that leads somewhere; a section that opens (turned). */
    val ChevronRight: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_chevron_right)

    /** A section that opens or closes (turned as it does). */
    val ChevronDown: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_chevron_down)

    /** A chip or key that opens a sheet or a menu. */
    val CaretDown: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_caret_down)

    /** Add, and more (the timeline's zoom, a stepper). */
    val Plus: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_plus)

    /** Less: a stepper. */
    val Minus: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_minus)

    /** How much of the graph's background shows. */
    val Contrast: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_contrast)

    /** The video's frame: its format and its crop. */
    val Frame: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_frame)

    /** The shot's start, placed by hand. */
    val Flag: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_flag)

    /** Done: a step taken, a video saved. */
    val Check: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_check)

    /** On to the next screen. */
    val ArrowRight: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_arrow_right)

    /** Play; how a recording starts, in the rules. */
    val Play: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_play)

    /** Pause. */
    val Pause: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_pause)

    /** How a recording stops, in the rules. */
    val Stop: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_stop)

    /** A name that can be changed. */
    val Edit: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_edit)

    /** A Bluetooth scale. */
    val Bluetooth: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_bluetooth)

    /** A link that opens outside the app. */
    val Launch: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_launch)

    /** Close, dismiss. */
    val Close: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_close)

    /** Nothing leaves the device. */
    val Shield: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_shield)

    /** An espresso machine. */
    val Machine: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_machine)

    /** A video. */
    val Film: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_film)

    /** A shot file. */
    val File: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_file)

    /** Told outside the app: a notification. */
    val Bell: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_bell)

    /** The share sheet. */
    val Share: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_share)

    /** The phone's gallery. */
    val Gallery: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_gallery)

    /** Settings. */
    val Settings: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_settings)

    /** Recording a shot. */
    val Record: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_record)

    /** About OnlyShots. */
    val Help: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_help)

    /** A shot start the recording fixed, until it is unlocked. */
    val Lock: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_lock)
}
