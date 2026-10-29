package app.onlyshots.home

import androidx.compose.runtime.Composable
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.component.BarIcon
import app.onlyshots.core.designsystem.component.BarPriority
import app.onlyshots.core.designsystem.component.NativeBarItem
import app.onlyshots.core.designsystem.component.NativeItems
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_record
import app.onlyshots.core.resources.home_add_by_address
import org.jetbrains.compose.resources.stringResource

/**
 * Where the bars are native (iPhone Duo): record, the screen's prominent action, and adding a machine by its address at
 * the far end. The record key on the screen stays: it is the page's own call to action.
 */
@Composable
internal fun HomeNativeBar(canRecord: Boolean, onRecord: () -> Unit, onAddByAddress: () -> Unit) {
    val record = stringResource(Res.string.core_record)
    val address = stringResource(Res.string.home_add_by_address)
    NativeItems(
        listOf(
            NativeBarItem(
                TestTags.HomeRecordAction,
                record,
                BarIcon.Record,
                prominent = true,
                enabled = canRecord,
                priority = BarPriority.High,
            ),
            NativeBarItem(TestTags.HomeAddByAddress, address, BarIcon.Add, bottom = true, priority = BarPriority.Low),
        )
    ) { id ->
        if (id == TestTags.HomeRecordAction) onRecord() else onAddByAddress()
    }
}
