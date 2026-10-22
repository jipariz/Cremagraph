package app.onlyshots.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.component.CardBar
import app.onlyshots.core.designsystem.component.CardBarButton
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_toolbar_data
import app.onlyshots.core.resources.edit_toolbar_style
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The toolbar over a selected graph, the same on the record and edit screens: its style, its data (and words), and how
 * much of its background shows. As wide as its keys, centred over the graph ([OverlayEditLayer] places it).
 */
@Composable
fun GraphToolbar(
    opacity: Float,
    onStyle: () -> Unit,
    onData: () -> Unit,
    onOpacity: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardBar(modifier) {
        CardBarButton(
            stringResource(Res.string.edit_toolbar_style),
            onClick = onStyle,
            modifier = Modifier.testTag(TestTags.ToolbarStyle),
            caret = true,
        )
        CardBarButton(
            stringResource(Res.string.edit_toolbar_data),
            onClick = onData,
            modifier = Modifier.testTag(TestTags.ToolbarData),
            caret = true,
        )
        CardBarButton(
            "${(opacity * 100).roundToInt()}%",
            onClick = { onOpacity(nextOpacity(opacity)) },
            icon = CgIcons.Contrast,
        )
    }
}

/** The opacity key's steps for the background: 100 → 75 → 50 → 25 → 0, then round again. */
fun nextOpacity(current: Float): Float =
    when {
        current > 0.875f -> 0.75f
        current > 0.625f -> 0.5f
        current > 0.375f -> 0.25f
        current > 0.125f -> 0f
        else -> 1f
    }
