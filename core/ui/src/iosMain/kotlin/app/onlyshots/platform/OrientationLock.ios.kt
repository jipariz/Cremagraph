package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskAllButUpsideDown
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations

/**
 * The orientations the app allows now. The host app's delegate answers `supportedInterfaceOrientationsFor` with this
 * (through `supportedOrientations()` in the app's iOS entry point), which is what makes a lock hold on iOS.
 */
object OrientationLock {
    var mask: ULong = UIInterfaceOrientationMaskAllButUpsideDown
}

@Composable
actual fun LockOrientation(landscape: Boolean) {
    DisposableEffect(landscape) {
        allow(if (landscape) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait)
        onDispose { allow(UIInterfaceOrientationMaskAllButUpsideDown) }
    }
}

/** Allows [mask] and turns the screen into it now, rather than at the next turn of the phone. */
private fun allow(mask: ULong) {
    OrientationLock.mask = mask
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene ?: return
    scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
    scene.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(mask), errorHandler = null)
}
