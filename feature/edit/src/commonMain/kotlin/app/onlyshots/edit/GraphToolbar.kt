package app.onlyshots.edit

import androidx.compose.runtime.Composable
import app.onlyshots.core.designsystem.component.CardBar
import app.onlyshots.core.designsystem.component.CardBarButton
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_toolbar_data
import app.onlyshots.core.resources.edit_toolbar_style
import app.onlyshots.core.resources.edit_toolbar_text
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The toolbar over a selected graph, the same on the record and edit screens: its style, its data, its words (only
 * where [onText] is given), how much of its background shows, and a reset to the default place and size.
 */
@Composable
fun GraphToolbar(
    opacity: Float,
    onStyle: () -> Unit,
    onData: () -> Unit,
    onOpacity: (Float) -> Unit,
    onReset: () -> Unit,
    onText: (() -> Unit)? = null,
) {
    CardBar {
        CardBarButton(stringResource(Res.string.edit_toolbar_style), onClick = onStyle)
        CardBarButton(stringResource(Res.string.edit_toolbar_data), onClick = onData)
        onText?.let { CardBarButton(stringResource(Res.string.edit_toolbar_text), onClick = it) }
        CardBarButton("◐ ${(opacity * 100).roundToInt()} %", onClick = { onOpacity(nextOpacity(opacity)) })
        CardBarButton("↺", onClick = onReset)
    }
}

/** The ◐ button's steps for the background: 100 → 75 → 50 → 25 → 0, then round again. */
fun nextOpacity(current: Float): Float =
    when {
        current > 0.875f -> 0.75f
        current > 0.625f -> 0.5f
        current > 0.375f -> 0.25f
        current > 0.125f -> 0f
        else -> 1f
    }
