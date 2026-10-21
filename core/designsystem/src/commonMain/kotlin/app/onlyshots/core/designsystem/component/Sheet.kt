package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.testTagsAsResourceIds

/**
 * A bottom sheet over a dimmed screen (Home's address, the recording settings). It looks like [EditorSheet], the
 * editor's sheet without a scrim: the same square top under a signal rule and [SheetHeader]. Its "done" closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgSheet(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTagsAsResourceIds(),
        containerColor = CgColors.Surface.Bar,
        contentColor = CgColors.Text.Primary,
        shape = CgShape.Sheet,
        sheetMaxWidth = 640.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .drawBehind { drawRect(CgColors.Brand.Signal, Offset.Zero, Size(size.width, CgStroke.Rule.toPx())) }
                .padding(top = CgStroke.Rule, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            SheetHeader(title, onDone = onDismiss)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = CgSpace.S16),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
            ) {
                content()
            }
        }
    }
}
