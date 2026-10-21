package app.onlyshots.uitests

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.onlyshots.TestTags
import app.onlyshots.editor.EditorApp
import app.onlyshots.overlay.GraphStyles
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The style editor from the editor's style picker: make a new style, change it, save it, find it among "your styles",
 * copy it from its long-press menu, remove the copy with the selected style's REMOVE key and the style itself from its
 * menu, which has no rename (the editor renames).
 */
@OptIn(ExperimentalTestApi::class)
class StyleEditorTest {
    private val app = Harness(phone = false)

    @BeforeTest fun start() = app.start()

    @AfterTest fun stop() = app.stop()

    @Test
    fun makesCopiesAndRemovesAStyle() =
        onDevice(Device.TabletLandscape, app.pickers, { EditorApp() }) {
            openTheEditor()
            tap(TestTags.Graph)
            tap(TestTags.ToolbarStyle)
            tap(TestTags.StyleNew)
            await(TestTags.ScreenStyleEditor)
            tap(TestTags.styleTab("grid"))
            tap(TestTags.choice("grid", "ROWS"))
            tap(TestTags.StyleEditorSave)
            tap(TestTags.StyleSaveConfirm)
            await(TestTags.ScreenEdit)

            // Back in the editor, with the graph's style page open again, the new style is the one tile that is no
            // built-in.
            if (
                onAllNodes(androidx.compose.ui.test.hasTestTag(TestTags.ToolbarStyle)).fetchSemanticsNodes().isEmpty()
            ) {
                tap(TestTags.Graph)
            }
            tap(TestTags.ToolbarStyle)
            openMenu()
            tap(TestTags.StyleMenuCopy)
            await(TestTags.ScreenStyleEditor)
            tap(TestTags.StyleEditorSave)
            tap(TestTags.StyleSaveConfirm)
            await(TestTags.ScreenEdit)
            if (
                onAllNodes(androidx.compose.ui.test.hasTestTag(TestTags.ToolbarStyle)).fetchSemanticsNodes().isEmpty()
            ) {
                tap(TestTags.Graph)
            }
            tap(TestTags.ToolbarStyle)
            waitUntil("the copy is in your styles", TIMEOUT) { customTiles().size == 2 }

            // Both go from their long-press menus: the copy, then the style itself.
            openMenu()
            tap(TestTags.StyleMenuDelete)
            await(TestTags.DialogConfirm).performSemanticsAction(SemanticsActions.OnClick)
            awaitGone(TestTags.DialogConfirm)
            waitUntil("one style left", TIMEOUT) { customTiles().size == 1 }
            openMenu()
            tap(TestTags.StyleMenuDelete)
            await(TestTags.DialogConfirm).performSemanticsAction(SemanticsActions.OnClick)
            waitUntil("the style is gone", TIMEOUT) { customTiles().isEmpty() }
        }

    private val builtIns = GraphStyles.all.map { TestTags.styleTile(it.id) }.toSet()
    private val isCustomTile =
        SemanticsMatcher("a custom style's tile") { node ->
            val tag = node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }
            tag != null && tag.startsWith(TestTags.styleTile("")) && tag !in builtIns
        }

    /**
     * Long-presses the custom style's tile until its menu is open. Right after a dialog closes, the desktop test window
     * spends the first press on giving focus back, as it does with the first tap.
     */
    private fun ComposeUiTest.openMenu() {
        repeat(3) {
            customTile().performTouchInput { longClick() }
            val open = runCatching {
                waitUntil(timeoutMillis = 3_000) {
                    onAllNodes(androidx.compose.ui.test.hasTestTag(TestTags.StyleMenuEdit))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }
                .isSuccess
            if (open) return
        }
        error("the style's menu never opened")
    }

    private fun ComposeUiTest.customTiles() = onAllNodes(isCustomTile, useUnmergedTree = true).fetchSemanticsNodes()

    private fun ComposeUiTest.customTile(): androidx.compose.ui.test.SemanticsNodeInteraction {
        waitUntil("a custom style", TIMEOUT) { customTiles().isNotEmpty() }
        return onAllNodes(isCustomTile, useUnmergedTree = true)[0]
    }
}
