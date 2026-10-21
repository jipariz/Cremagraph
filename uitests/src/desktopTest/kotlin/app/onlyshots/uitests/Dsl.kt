package app.onlyshots.uitests

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Long enough for FFmpeg to probe and thumbnail the clip, or to export it, on a slow CI machine. */
const val TIMEOUT = 30_000L

/** Waits until exactly one node carries [tag] (a screen arrived, a control appeared), and returns it. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.await(tag: String, timeoutMs: Long = TIMEOUT): SemanticsNodeInteraction {
    waitUntil("one node tagged $tag", timeoutMs) {
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }
    return onNodeWithTag(tag, useUnmergedTree = true)
}

/** Waits until nothing carries [tag] any more (a sheet closed, a screen left). */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitGone(tag: String, timeoutMs: Long = TIMEOUT) {
    waitUntil("no node tagged $tag", timeoutMs) {
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }
}

/** Waits until the node tagged [tag] is enabled, then taps it. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.tap(tag: String, timeoutMs: Long = TIMEOUT) {
    val node = await(tag, timeoutMs)
    waitUntil("$tag enabled", timeoutMs) { runCatching { node.assertIsEnabled() }.isSuccess }
    runCatching { node.performScrollTo() }
    node.performClick()
}
