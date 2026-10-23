package app.onlyshots.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.onlyshots.core.model.Shot
import org.koin.compose.viewmodel.koinViewModel

/**
 * The machine shot picker on its own, opened from the editor's shot sheet: the same machines and recent shots as "edit
 * a video"'s, and the loaded shot goes to [onPicked].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MachineShotsScreen(
    onPicked: (Shot) -> Unit,
    onBack: () -> Unit,
    viewModel: MachineShotsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.openMachines() }
    LaunchedEffect(state.shot) {
        state.shot?.let {
            viewModel.opened()
            onPicked(it)
        }
    }
    // From a machine's shots back to the machines; from the machines back to the editor.
    val back = { if (!viewModel.back()) onBack() }
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None)) { back() }
    MachineShotsBody(
        state = state,
        onMachine = viewModel::chooseMachine,
        onShot = viewModel::chooseShot,
        onBack = back,
    )
}
