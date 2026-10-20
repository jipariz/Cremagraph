package app.onlyshots.layout

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity

/**
 * The adaptive library's window info with the fold the host reported ([IosHinge]): the library sees no hinge on iOS.
 * Half open, the fold separates the window (tabletop when it lies across it); fully open, the inner display is one
 * plain wide window.
 */
@Composable
actual fun platformWindowAdaptiveInfo(): WindowAdaptiveInfo {
    val info = currentWindowAdaptiveInfo()
    val fold by IosHinge.fold.collectAsState()
    val density = LocalDensity.current.density
    return foldedInfo(info, fold, density)
}

/** [info] with [fold] as its posture; its bounds in pixels, as the library's hinges are. */
internal fun foldedInfo(info: WindowAdaptiveInfo, fold: IosFold?, density: Float): WindowAdaptiveInfo {
    fold ?: return info
    val hinge =
        HingeInfo(
            bounds = Rect(fold.left * density, fold.top * density, fold.right * density, fold.bottom * density),
            isFlat = !fold.partiallyOpen,
            isVertical = fold.isVertical,
            isSeparating = fold.partiallyOpen,
            isOccluding = false,
        )
    return WindowAdaptiveInfo(
        info.windowSizeClass,
        Posture(isTabletop = fold.partiallyOpen && !fold.isVertical, hingeList = listOf(hinge)),
    )
}
