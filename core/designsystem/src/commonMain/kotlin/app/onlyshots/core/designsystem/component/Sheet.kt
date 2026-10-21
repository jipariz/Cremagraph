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
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.testTagsAsResourceIds

/**
 * A bottom sheet over a dimmed screen (Home's address, the recording settings). It looks like [EditorSheet], the
 * editor's sheet without a scrim: the same rounded top, handle and [SheetHeader]. Its "done" closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTagsAsResourceIds(),
        containerColor = CgColors.Surface.Bar,
        contentColor = CgColors.Text.Primary,
        shape = CgShape.Sheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = CgSpace.S16, end = CgSpace.S16, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            SheetHeader(title, onDone = onDismiss)
            content()
        }
    }
}
