package app.onlyshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** A navigation destination's root, named for UI tests ([TestTags]); lays its screen out exactly as without it. */
@Composable
fun TaggedScreen(tag: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().testTag(tag), propagateMinConstraints = true) { content() }
}
