@file:OptIn(ExperimentalForeignApi::class)

package app.onlyshots.video

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.preferredTransform

/**
 * Rotation the track asks for, 0, 90, 180 or 270 (phones store portrait video as rotated landscape). Whoever draws the
 * frames themselves - the exporter, the iOS video surface - has to turn them; a player layer does it on its own.
 */
fun AVAssetTrack.rotationDegrees(): Int = preferredTransform.useContents {
    val deg = atan2(b, a) * 180.0 / PI
    (((deg / 90.0).roundToInt() * 90) % 360 + 360) % 360
}
