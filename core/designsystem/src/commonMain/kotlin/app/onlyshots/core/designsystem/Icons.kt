package app.onlyshots.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.ic_film
import app.onlyshots.core.resources.ic_help
import app.onlyshots.core.resources.ic_lock
import app.onlyshots.core.resources.ic_machine
import app.onlyshots.core.resources.ic_onlyshots
import app.onlyshots.core.resources.ic_play
import app.onlyshots.core.resources.ic_plus
import app.onlyshots.core.resources.ic_record
import app.onlyshots.core.resources.ic_settings
import app.onlyshots.core.resources.ic_stop
import org.jetbrains.compose.resources.vectorResource

/**
 * The app's icons, from the vector drawables in :core:resources (`drawable/ic_*.xml`, the Figma file's exported SVG
 * paths). The single-colour ones are drawn in white and tinted where they are used.
 */
object CgIcons {
    /**
     * The Signal tile in its small cut (design/logo/kit/onlyshots-symbol-small.svg), the weight the logo guidelines set
     * for 16 to 47 px: a signal square with the shot's trace cut in from its left edge to a square pen head on a stem.
     */
    val OnlyShots: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_onlyshots)

    /** Figma Icon/machine (1:125). */
    val Machine: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_machine)

    /** Figma Icon/plus (1:96). */
    val Plus: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_plus)

    /** A strip of film with a play mark: the drop zone's video. */
    val Film: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_film)

    /** A gear: a ring, eight teeth and a hub. */
    val Settings: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_settings)

    /** The record mark: a dot in a ring. */
    val Record: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_record)

    /** A question mark in a ring: "about OnlyShots". */
    val Help: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_help)

    /** A closed padlock: a shot start the recording fixed, until it is unlocked. */
    val Lock: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_lock)

    /** A rounded triangle: how a recording starts, in the rules strip. */
    val Play: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_play)

    /** A rounded square: how a recording stops, in the rules strip. */
    val Stop: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_stop)
}
