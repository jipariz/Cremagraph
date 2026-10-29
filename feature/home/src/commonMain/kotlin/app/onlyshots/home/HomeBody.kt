package app.onlyshots.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.machine.FoundMachine

/**
 * Figma "Home" without the view model, so every state can be drawn from a plain [HomeUiState]. A phone has one column,
 * the record action under the sources; a wide window has two pages, the sources and the next take.
 */
@Composable
fun HomeBody(
    state: HomeUiState,
    onMachine: (FoundMachine) -> Unit,
    onScale: (FoundBleDevice) -> Unit,
    onAddByAddress: () -> Unit,
    onAllowBluetooth: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
    /** The next take as a provider: the live screen's changes with every sample, read only where it is drawn. */
    take: () -> NextTake = { NextTake() },
    onRecording: ((RecordingSettings) -> RecordingSettings) -> Unit = {},
    onSample: () -> Unit = {},
    onBack: () -> Unit = {},
    /**
     * On a book foldable, what a phone shows in a sheet (adding a machine by its address) takes the right page, in
     * place of the next take, and the left one dims; [onFoldPageDismiss] closes it from there.
     */
    foldPage: (@Composable () -> Unit)? = null,
    onFoldPageDismiss: () -> Unit = {},
) {
    val layout = LocalWindowLayout.current
    HomeNativeBar(recordAction(state).enabled, onOpenCamera, onAddByAddress)
    when (layout.kind) {
        LayoutKind.Wide ->
            HomeWideLayout(
                layout = layout,
                state = state,
                onMachine = onMachine,
                onScale = onScale,
                onAddByAddress = onAddByAddress,
                onAllowBluetooth = onAllowBluetooth,
                onOpenCamera = onOpenCamera,
                take = take,
                onRecording = onRecording,
                onSample = onSample,
                onBack = onBack,
                foldPage = foldPage,
                onFoldPageDismiss = onFoldPageDismiss,
                modifier = modifier,
            )
        LayoutKind.Phone,
        LayoutKind.Stacked,
        LayoutKind.Tiny ->
            HomePhoneLayout(
                state = state,
                onMachine = onMachine,
                onScale = onScale,
                onAddByAddress = onAddByAddress,
                onAllowBluetooth = onAllowBluetooth,
                onOpenCamera = onOpenCamera,
                onSample = onSample,
                onBack = onBack,
                modifier = modifier,
            )
    }
}
