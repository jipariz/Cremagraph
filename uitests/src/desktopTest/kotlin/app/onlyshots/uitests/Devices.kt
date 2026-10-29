package app.onlyshots.uitests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.onlyshots.layout.LocalWindowLayoutOverride
import app.onlyshots.layout.WindowLayout
import app.onlyshots.layout.bookLayout
import app.onlyshots.layout.stackedLayout
import app.onlyshots.layout.tinyLayout
import app.onlyshots.platform.LocalPickerOverride
import app.onlyshots.platform.PickerOverride
import kotlin.time.Duration.Companion.minutes

/**
 * The windows the flows run in: the screenshot tests' devices (Mugshot's PHONE, TABLET, FOLDABLE) and the postures
 * `FoldablePreviews` hands the app. A fold can't be a desktop test window's, so [layout] forces it through the app's
 * own `ProvideWindowLayout`; the others let the window decide, as in the apps.
 */
enum class Device(
    val widthDp: Int,
    val heightDp: Int,
    val layout: ((Float, Float) -> WindowLayout)? = null,
    /** The window the editor's flow can run end to end in (Tiny says "open the phone"; tabletop has no export bar). */
    val edits: Boolean = true,
) {
    Phone(411, 923),
    PhoneLandscape(923, 411),
    TabletLandscape(1280, 800),
    TabletPortrait(800, 1280),
    BookFold(701, 841, { w, h -> bookLayout(w, h) }),
    Tabletop(1280, 800, { w, h -> stackedLayout(w, h) }, edits = false),
    Flex(411, 923, { w, h -> stackedLayout(w, h) }, edits = false),
    Cover(360, 420, { w, h -> tinyLayout(w, h) }, edits = false),
}

/** Runs [block] in a desktop test window of [device]'s size and posture, with [pickers] answering the app's pickers. */
@OptIn(ExperimentalTestApi::class)
fun onDevice(device: Device, pickers: PickerOverride, app: @Composable () -> Unit, block: ComposeUiTest.() -> Unit) =
    runDesktopComposeUiTest(width = device.widthDp, height = device.heightDp, testTimeout = 5.minutes) {
        setContent {
            CompositionLocalProvider(
                LocalPickerOverride provides pickers,
                LocalWindowLayoutOverride provides device.layout,
            ) {
                app()
            }
        }
        block()
    }
