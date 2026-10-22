package app.onlyshots.uitests

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import app.onlyshots.OnlyShotsApp
import app.onlyshots.TestTags
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phones' app on every window it adapts to: record the sample shot and export it, compose a video from a picked
 * file and shot, and the about sheet's privacy screen. The camera "records" the test clip; the export is FFmpeg's.
 */
@OptIn(ExperimentalTestApi::class)
class PhoneAppTest {
    private val app = Harness(phone = true)

    @BeforeTest fun start() = app.start()

    @AfterTest fun stop() = app.stop()

    private fun run(device: Device, block: ComposeUiTest.() -> Unit) =
        onDevice(device, app.pickers, { OnlyShotsApp() }, block)

    // Record the sample shot, then edit and export the take.
    @Test fun recordAndExportOnAPhone() = run(Device.Phone) { recordAndExport(Device.Phone) }

    @Test fun recordAndExportOnALandscapePhone() = run(Device.PhoneLandscape) { recordAndExport(Device.PhoneLandscape) }

    @Test fun recordAndExportOnATablet() = run(Device.TabletLandscape) { recordAndExport(Device.TabletLandscape) }

    @Test fun recordAndExportOnAnUprightTablet() = run(Device.TabletPortrait) { recordAndExport(Device.TabletPortrait) }

    @Test fun recordAndExportOnABookFold() = run(Device.BookFold) { recordAndExport(Device.BookFold) }

    @Test fun recordOnATabletop() = run(Device.Tabletop) { recordAndExport(Device.Tabletop) }

    @Test fun recordInFlexMode() = run(Device.Flex) { recordAndExport(Device.Flex) }

    @Test fun recordOnACoverScreen() = run(Device.Cover) { recordAndExport(Device.Cover) }

    // Compose a video from a picked file and shot.
    @Test fun composeOnAPhone() = run(Device.Phone) { composeAndLeave() }

    @Test fun composeOnATablet() = run(Device.TabletLandscape) { composeAndLeave() }

    @Test fun composeOnABookFold() = run(Device.BookFold) { composeAndLeave() }

    // About → privacy → back.
    @Test fun privacyOnAPhone() = run(Device.Phone) { privacyAndBack() }

    @Test fun privacyOnATablet() = run(Device.TabletLandscape) { privacyAndBack() }

    /**
     * Start → record a shot → Home → try the sample shot → Record → record → stop → Edit; where the editor edits, back
     * asks before leaving the take, then export it to the gallery and go on to the next shot.
     */
    private fun ComposeUiTest.recordAndExport(device: Device) {
        await(TestTags.ScreenStart)
        tap(TestTags.StartRecord)
        await(TestTags.ScreenHome)
        tap(TestTags.HomeSample)
        await(TestTags.ScreenRecord)
        tap(TestTags.RecordButton)
        // A take of a second and a half: long enough to be a recording, not a tap.
        Thread.sleep(1_500)
        tap(TestTags.RecordButton)
        await(TestTags.ScreenEdit)
        if (!device.edits) return
        // A take not saved anywhere yet can't be opened again: back asks first, and "keep editing" stays.
        tap(TestTags.Back)
        tap(TestTags.DialogConfirm)
        awaitGone(TestTags.DialogConfirm)
        await(TestTags.ScreenEdit)
        // The phone opens its export sheet from the top bar; a wide window has the export bar in its panel.
        tapIfPresent(TestTags.EditExport)
        tap(TestTags.ExportSave)
        await(TestTags.ExportNext, EXPORT_TIMEOUT)
        assertEquals(1, app.gallery.saved.size, "the take went to the gallery")
        // The next shot starts from Home, where its machine or scale is picked.
        tap(TestTags.ExportNext)
        await(TestTags.ScreenHome)
    }

    /** Start → compose a video → pick both → open the editor → back to Open. */
    private fun ComposeUiTest.composeAndLeave() {
        await(TestTags.ScreenStart)
        tap(TestTags.StartCompose)
        await(TestTags.ScreenOpen)
        tap(TestTags.OpenPickVideo)
        tap(TestTags.OpenPickShot)
        // The phones offer a file or the machine.
        tap(TestTags.OpenFromFile)
        tap(TestTags.OpenEditor)
        await(TestTags.ScreenEdit)
        // A video composed by hand stays in the library, so back leaves without asking.
        tap(TestTags.Back)
        await(TestTags.ScreenOpen)
    }

    /** Start → ? → privacy and support → the privacy screen → back to Start. */
    private fun ComposeUiTest.privacyAndBack() {
        await(TestTags.ScreenStart)
        tap(TestTags.StartHelp)
        tap(TestTags.HelpPrivacy)
        await(TestTags.ScreenPrivacy)
        tap(TestTags.Back)
        await(TestTags.ScreenStart)
    }
}

/** Taps [tag] if it is there within a moment; for controls only some layouts have. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.tapIfPresent(tag: String) {
    val there = runCatching {
        waitUntil(timeoutMillis = 3_000) {
            onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
    }
        .isSuccess
    if (there) tap(tag)
}

/** How the record screen behaves while a take runs, on a phone: back inert until the take is stopped and saved. */
@OptIn(ExperimentalTestApi::class)
class RecordingLookTest {
    private val app = Harness(phone = true)

    @BeforeTest fun start() = app.start()

    @AfterTest fun stop() = app.stop()

    @Test
    fun aTakeHoldsBack() =
        onDevice(Device.Phone, app.pickers, { OnlyShotsApp() }) {
            await(TestTags.ScreenStart)
            tap(TestTags.StartRecord)
            await(TestTags.ScreenHome)
            tap(TestTags.HomeSample)
            await(TestTags.ScreenRecord)
            await(TestTags.Back).assertIsEnabled()
            tap(TestTags.RecordButton)
            waitUntil("back held while recording", TIMEOUT) {
                runCatching { onNodeWithTag(TestTags.Back, useUnmergedTree = true).assertIsNotEnabled() }.isSuccess
            }
            Thread.sleep(1_500)
            tap(TestTags.RecordButton)
            await(TestTags.ScreenEdit)
        }
}

/**
 * A take not saved anywhere asks before it is left, the safe choice on the left: keep editing stays in the editor,
 * leave (on the right) goes back to record.
 */
@OptIn(ExperimentalTestApi::class)
class LeaveDialogTest {
    private val app = Harness(phone = true)

    @BeforeTest fun start() = app.start()

    @AfterTest fun stop() = app.stop()

    @Test
    fun leaveIsTheRightHandChoice() =
        onDevice(Device.Phone, app.pickers, { OnlyShotsApp() }) {
            await(TestTags.ScreenStart)
            tap(TestTags.StartRecord)
            tap(TestTags.HomeSample)
            await(TestTags.ScreenRecord)
            tap(TestTags.RecordButton)
            Thread.sleep(1_500)
            tap(TestTags.RecordButton)
            await(TestTags.ScreenEdit)
            tap(TestTags.Back)
            val keep = await(TestTags.DialogConfirm).getUnclippedBoundsInRoot()
            val leave = await(TestTags.DialogDismiss).getUnclippedBoundsInRoot()
            assertTrue(keep.right <= leave.left, "keep editing on the left, leave on the right")
            tap(TestTags.DialogDismiss)
            awaitGone(TestTags.ScreenEdit)
        }
}
