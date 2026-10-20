package app.onlyshots

import androidx.compose.ui.window.ComposeUIViewController
import app.onlyshots.di.initKoin
import app.onlyshots.layout.IosHinge
import platform.UIKit.UIViewController

private var koinStarted = false

/** Entry point for the iOS host app (SwiftUI `UIViewControllerRepresentable`). */
fun MainViewController(): UIViewController {
    if (!koinStarted) {
        @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
        DevTools.enabled = Platform.isDebugBinary
        initKoin()
        koinStarted = true
    }
    return ComposeUIViewController { OnlyShotsApp() }
}

/**
 * The orientations the app allows now, for the host's `application(_:supportedInterfaceOrientationsFor:)`: the record
 * screen holds the screen the way its video is filmed (`LockOrientation`).
 */
fun supportedOrientations(): ULong = app.onlyshots.platform.OrientationLock.mask

/**
 * The fold of a foldable iPhone, from the host (iOS 27.1's hinge API, which Kotlin's bindings do not have yet): whether
 * it is half open, and where it crosses the window, in points.
 */
fun foldChanged(partiallyOpen: Boolean, left: Double, top: Double, right: Double, bottom: Double) =
    IosHinge.update(partiallyOpen, left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/** No fold to lay out around: a phone without one, or one closed onto its outer display. */
fun foldGone() = IosHinge.clear()
