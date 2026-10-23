package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.layout.FoldScrim
import app.onlyshots.layout.WidePanes
import app.onlyshots.layout.WindowLayout
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.machine.FoundMachine

/**
 * A wide Home: two pages, where the shot comes from and the take the record button starts. On a book foldable a
 * [foldPage] takes the second page in place of the take, and the first dims under it.
 */
@Composable
internal fun HomeWideLayout(
    layout: WindowLayout,
    state: HomeUiState,
    onMachine: (FoundMachine) -> Unit,
    onScale: (FoundBleDevice) -> Unit,
    onAddByAddress: () -> Unit,
    onAllowBluetooth: () -> Unit,
    onOpenCamera: () -> Unit,
    take: () -> NextTake,
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit,
    onSample: () -> Unit,
    onBack: () -> Unit,
    foldPage: (@Composable () -> Unit)?,
    onFoldPageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base)) {
        WidePanes(
            layout,
            Modifier.fillMaxSize().safeDrawingPadding(),
            main = {
                Box(Modifier.fillMaxSize()) {
                    SourcesPage(layout, state, onMachine, onScale, onAddByAddress, onAllowBluetooth, onBack)
                    if (foldPage != null) FoldScrim(onFoldPageDismiss)
                }
            },
            panel = {
                if (foldPage != null) foldPage() else NextTakePane(state, take, onRecording, onOpenCamera, onSample)
            },
        )
    }
}

/** The first page: the header over the machines and scales, side by side where the page has room for both. */
@Composable
private fun SourcesPage(
    layout: WindowLayout,
    state: HomeUiState,
    onMachine: (FoundMachine) -> Unit,
    onScale: (FoundBleDevice) -> Unit,
    onAddByAddress: () -> Unit,
    onAllowBluetooth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Measured for fit, not to choose a layout: whether both lists fit across this page.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sideBySide = sourcesSideBySide(layout.fold, maxWidth.value)
        val margin = if (layout.fold) CgSpace.S16 else PAGE_MARGIN
        Column(Modifier.fillMaxSize().padding(horizontal = margin - CgSpace.S16)) {
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
                    .padding(horizontal = CgSpace.S16)
                    .padding(top = if (sideBySide) CgSpace.S16 else CgSpace.S6, bottom = CgSpace.S24),
                sideBySide = sideBySide,
            )
        }
    }
}

/** The first page's side margin without a fold; a fold's page keeps the phone's. */
private val PAGE_MARGIN = 32.dp
