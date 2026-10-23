package app.onlyshots.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.layout.PANEL_WIDTH
import app.onlyshots.layout.WidePanes
import app.onlyshots.layout.WindowLayout

/** A wide start, in the [shape] [startWideShape] decided. */
@Composable
internal fun StartWideLayout(
    layout: WindowLayout,
    shape: StartWideShape,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onHelp: () -> Unit,
    helpPage: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (shape) {
        StartWideShape.Beside -> StartBeside(layout, onRecord, onEdit, onHelp, helpPage, modifier)
        StartWideShape.Upright -> StartUpright(onRecord, onEdit, onHelp, modifier)
    }
}

/**
 * On its side: the headline on the left, the choices in a panel on the right. On a book foldable each is a page, drawn
 * smaller, and the help ([helpPage]) takes the right one when it is open.
 */
@Composable
private fun StartBeside(
    layout: WindowLayout,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onHelp: () -> Unit,
    helpPage: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val fold = layout.fold
    val top = if (fold) FOLD_TOP else WIDE_TOP
    WidePanes(
        layout,
        modifier,
        panelWidth = PANEL_WIDTH,
        gap = false,
        main = {
            Column(
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = if (fold) CgSpace.S16 else WIDE_MARGIN)
                    .padding(top = top, bottom = WIDE_BOTTOM),
                verticalArrangement = Arrangement.spacedBy(WIDE_GAP),
            ) {
                StartHeader(onHelp = null, big = !fold)
                GraphCard(Modifier.weight(1f), big = !fold)
                Headline(big = !fold)
            }
        },
    ) {
        if (fold && helpPage != null) {
            helpPage()
            return@WidePanes
        }
        Column(
            Modifier.fillMaxSize()
                .drawBehind {
                    drawLine(CgColors.Line.Hairline, Offset.Zero, Offset(0f, size.height), CgStroke.Hairline.toPx())
                }
                .safeDrawingPadding()
                .padding(horizontal = if (fold) CgSpace.S16 else CgSpace.S24)
                .padding(top = top, bottom = WIDE_BOTTOM),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { HelpKey(onHelp) }
            Spacer(Modifier.weight(1f))
            Choices(onRecord, onEdit, sideBySide = false)
        }
    }
}

/** Upright: one column, the help in the header and the two choices side by side under the headline. */
@Composable
private fun StartUpright(onRecord: () -> Unit, onEdit: () -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = UPRIGHT_MARGIN, vertical = UPRIGHT_MARGIN),
        verticalArrangement = Arrangement.spacedBy(UPRIGHT_GAP),
    ) {
        StartHeader(onHelp = onHelp, big = true)
        GraphCard(Modifier.weight(1f), big = true)
        Headline(big = true)
        Choices(onRecord, onEdit, sideBySide = true)
    }
}

/** The headline page's side margin on its side; a fold's page keeps the phone's. */
private val WIDE_MARGIN = 48.dp

/** Above both sides on its side; a fold's smaller pages start a little lower. */
private val WIDE_TOP = 40.dp
private val FOLD_TOP = 48.dp
private val WIDE_BOTTOM = 32.dp

/** Between the headline page's header, card and headline. */
private val WIDE_GAP = 28.dp

/** An upright tablet's margin all round, and the gap between its groups. */
private val UPRIGHT_MARGIN = 40.dp
private val UPRIGHT_GAP = 32.dp
