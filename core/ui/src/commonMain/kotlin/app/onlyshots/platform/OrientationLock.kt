package app.onlyshots.platform

import androidx.compose.runtime.Composable

/**
 * Holds the screen [landscape] or portrait while this is composed, and lets it turn freely again after: the record
 * screen holds it the way the video is filmed, so the camera records upright in the chosen shape. Nothing on the
 * desktop and the web, which have no camera.
 */
@Composable expect fun LockOrientation(landscape: Boolean)
