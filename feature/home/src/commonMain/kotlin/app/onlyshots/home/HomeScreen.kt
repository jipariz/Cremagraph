package app.onlyshots.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.platform.rememberBluetoothPermission
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home: where the next take's data comes from (a machine on the network or a Bluetooth scale), searched for while it is
 * in front, and the way on to the camera.
 */
@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onTrySample: () -> Unit = onOpenCamera,
    onBack: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The next take changes with every live sample: handed down as a provider, read only where it is shown.
    val take = viewModel.take.collectAsStateWithLifecycle()
    val askBluetooth = rememberBluetoothPermission(viewModel::onBluetoothAnswer)
    // Scales are searched for all the time, so Bluetooth is asked for as Home first opens; already allowed, this
    // answers at once without a prompt. Refused, the "connect a scale" card asks again.
    LaunchedEffect(Unit) { if (!viewModel.askedForBluetooth) askBluetooth() }
    LifecycleStartEffect(viewModel) {
        viewModel.endSample()
        viewModel.startSearching()
        onStopOrDispose { viewModel.stopSearching() }
    }
    // On a book foldable, adding a machine by its address takes the right page; elsewhere it is a sheet.
    val fold = LocalWindowLayout.current.fold
    HomeBody(
        state = state,
        onMachine = viewModel::connectTo,
        onScale = viewModel::connectScale,
        onAddByAddress = viewModel::openAddress,
        onAllowBluetooth = askBluetooth,
        // Recording without data is just that: whatever the sample left behind is put back first.
        onOpenCamera = {
            viewModel.endSample()
            onOpenCamera()
        },
        // The sample needs nothing to be picked: the camera opens and the shot plays as soon as it is armed.
        onSample = {
            viewModel.useSample()
            onTrySample()
        },
        onBack = onBack,
        take = { take.value },
        onRecording = viewModel::updateRecording,
        foldPage =
            if (state.addressSheet && fold) {
                {
                    AddressPage(
                        address = state.address,
                        onAddress = viewModel::setAddress,
                        onConnect = viewModel::addByAddress,
                        onDismiss = viewModel::closeAddress,
                    )
                }
            } else {
                null
            },
        onFoldPageDismiss = viewModel::closeAddress,
    )
    if (state.addressSheet && !fold) {
        AddressSheet(
            address = state.address,
            onAddress = viewModel::setAddress,
            onConnect = viewModel::addByAddress,
            onDismiss = viewModel::closeAddress,
        )
    }
}
