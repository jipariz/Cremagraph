package app.onlyshots.uitests

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performTextInput
import app.onlyshots.TestTags
import app.onlyshots.core.model.Series
import app.onlyshots.editor.EditorApp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The desktop and web editor, start to finish, on a desktop window: pick a video and its shot, open the editor, restyle
 * the graph and take it back, change what it shows, title it, reframe the video, export it (FFmpeg, for real) to the
 * gallery and go back for another.
 */
@OptIn(ExperimentalTestApi::class)
class EditorAppTest {
    private val app = Harness(phone = false)

    @BeforeTest fun start() = app.start()

    @AfterTest fun stop() = app.stop()

    @Test fun desktopWindow() = editAndExport(Device.TabletLandscape)

    @Test fun uprightTablet() = editAndExport(Device.TabletPortrait)

    /**
     * The editor in a phone-sized browser window: the phone layout, with the toolbar over the selected graph and its
     * pages as sheets, the frame switch on the picture and the export as a sheet.
     */
    @Test
    fun phoneSizedWindow() =
        onDevice(Device.Phone, app.pickers, { EditorApp() }) {
            openTheEditor()
            tap(TestTags.Graph)
            tap(TestTags.ToolbarStyle)
            tap(TestTags.styleTile("metro"))
            tap(TestTags.SheetDone)
            // The data sheet holds the graph's words too.
            tap(TestTags.ToolbarData)
            tap(TestTags.series(Series.TEMPERATURE.name))
            await(TestTags.TextTitleField).performTextInput("Morning shot")
            tap(TestTags.SheetDone)
            tap(TestTags.EditFrame)
            tap(TestTags.format("1:1"))
            tap(TestTags.EditFrame)
            tap(TestTags.EditExport)
            tap(TestTags.ExportSave)
            await(TestTags.ExportNext, EXPORT_TIMEOUT)
            assertEquals(1, app.gallery.saved.size, "the video went to the gallery")
            tap(TestTags.ExportNext)
            await(TestTags.ScreenOpen)
        }

    private fun editAndExport(device: Device) =
        onDevice(device, app.pickers, { EditorApp() }) {
            openTheEditor()

            // The graph, selected: its panel's pages.
            tap(TestTags.Graph)
            tap(TestTags.ToolbarStyle)
            tap(TestTags.styleTile("metro"))
            tap(TestTags.ToolbarData)
            tap(TestTags.series(Series.TEMPERATURE.name))
            await(TestTags.TextTitleField).performTextInput("Morning shot")
            tap(TestTags.SheetDone)

            // The video: its frame, then the export.
            tap(TestTags.format("1:1"))
            tap(TestTags.ExportSave)
            await(TestTags.ExportNext, EXPORT_TIMEOUT)
            assertEquals(1, app.gallery.saved.size, "the video went to the gallery")
            tap(TestTags.ExportNext)
            await(TestTags.ScreenOpen)
        }
}

/** Open → pick the video → pick the shot → open the editor. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openTheEditor() {
    await(TestTags.ScreenOpen)
    tap(TestTags.OpenPickVideo)
    tap(TestTags.OpenPickShot)
    tap(TestTags.OpenEditor)
    await(TestTags.ScreenEdit)
}

/** An FFmpeg render of the test clip, with margin for a busy machine. */
const val EXPORT_TIMEOUT = 120_000L
