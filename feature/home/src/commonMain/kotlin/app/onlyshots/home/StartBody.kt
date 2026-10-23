package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout

/**
 * The start screen from plain callbacks, so the screenshot tests can draw it. A phone has one column, the choices at
 * the bottom where the thumb is. A wide window turned on its side puts the headline on the left and the choices in a
 * panel on the right (one page each on a book foldable); an upright one stays a column, the two choices side by side.
 */
@Composable
fun StartBody(
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onHelp: () -> Unit = {},
    /** On a book foldable, the help as the right page, in place of the choices. */
    helpPage: (@Composable () -> Unit)? = null,
) {
    val layout = LocalWindowLayout.current
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).drawBehind { drawGrid() }) {
        when (layout.kind) {
            LayoutKind.Wide -> StartWideLayout(layout, startWideShape(layout), onRecord, onEdit, onHelp, helpPage)
            LayoutKind.Phone,
            LayoutKind.Stacked,
            LayoutKind.Tiny -> StartPhoneLayout(onRecord, onEdit, onHelp)
        }
    }
}
