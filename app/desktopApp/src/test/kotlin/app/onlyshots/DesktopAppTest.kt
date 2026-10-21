package app.onlyshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.onlyshots.di.initKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import org.koin.core.context.stopKoin

/**
 * The desktop app as `main` starts it: its own Koin (the desktop platform module, FFmpeg, the library under a throwaway
 * home), the splash and the editor's first screen. The editor's flows are `:uitests`'.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAppTest {
    @AfterTest fun stop() = stopKoin()

    @Test
    fun startsOnTheOpenScreen() =
        runDesktopComposeUiTest(width = 1280, height = 860) {
            initKoin()
            setContent { DesktopEditor(SplashState(held = false)) }
            waitUntil("the Open screen with its video step", 30_000) {
                onAllNodes(hasTestTag(TestTags.ScreenOpen)).fetchSemanticsNodes().isNotEmpty() &&
                    onAllNodes(hasTestTag(TestTags.OpenPickVideo), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
            }
        }
}
