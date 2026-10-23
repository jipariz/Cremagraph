package app.onlyshots.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.component.SheetHeader
import app.onlyshots.core.designsystem.component.topRule
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_dismiss
import org.jetbrains.compose.resources.stringResource

/*
 * What a sheet becomes on a book foldable ([WindowLayout.fold]): a page of its own beside the one it was opened from,
 * which dims under [FoldScrim] until the page is done.
 */

/**
 * A sheet as a fold's page: its header under a signal rule, then [content]. [contentPadding] keeps both clear of the
 * window's edges where the page meets them; the rule stays at the page's top.
 */
@Composable
fun FoldPage(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().topRule().padding(top = CgStroke.Rule).padding(contentPadding)) {
        SheetHeader(title, onDone = onDismiss)
        content()
    }
}

/** The page a [FoldPage] was opened from, dimmed: a tap on it closes the other. Said so, and no ripple over a page. */
@Composable
fun FoldScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(CgColors.Surface.Scrim.copy(alpha = FOLD_SCRIM_ALPHA))
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(Res.string.core_dismiss),
                onClick = onDismiss,
            )
    )
}

private const val FOLD_SCRIM_ALPHA = 0.6f
