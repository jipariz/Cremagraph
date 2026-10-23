package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.component.RecordAction
import app.onlyshots.core.resources.text
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.machine.FoundMachine

/** A phone's Home: one column, the sources scrolling, the record action at the bottom where the thumb is. */
@Composable
internal fun HomePhoneLayout(
    state: HomeUiState,
    onMachine: (FoundMachine) -> Unit,
    onScale: (FoundBleDevice) -> Unit,
    onAddByAddress: () -> Unit,
    onAllowBluetooth: () -> Unit,
    onOpenCamera: () -> Unit,
    onSample: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        // The design is a phone column; on a tablet it stays one, centred.
        Column(Modifier.widthIn(max = PHONE_COLUMN_WIDTH).fillMaxSize()) {
            HomeHeader(onBack)
            Sources(
                state,
                onMachine,
                onScale,
                onAddByAddress,
                onAllowBluetooth,
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = CgSpace.S16, end = CgSpace.S16, top = CgSpace.S6, bottom = CgSpace.S16),
            )
            val action = recordAction(state)
            Column(
                Modifier.padding(start = CgSpace.S16, end = CgSpace.S16, top = CgSpace.S12, bottom = CgSpace.S8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RecordAction(
                    text = action.text.text(),
                    caption = action.caption.text(),
                    enabled = action.enabled,
                    onClick = onOpenCamera,
                    modifier = Modifier.testTag(TestTags.HomeRecordAction),
                    withData = action.withData,
                )
                SampleLink(onSample)
            }
        }
    }
}
