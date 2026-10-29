package app.onlyshots.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.sources.machine.FoundMachine

/**
 * "From the machine": the machines on this Wi-Fi, searched for as on Home; then the chosen one's recent shots, read
 * over its API. A tap on a shot loads it and goes back to the steps. A phone takes the two one after the other; a wide
 * window shows both, the machines on the left and the chosen one's shots on the right (a page each on a book foldable).
 */
@Composable
fun MachineShotsBody(
    state: MachineShotsUiState,
    onMachine: (FoundMachine) -> Unit,
    onShot: (ShotSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWindowLayout.current
    when (layout.kind) {
        LayoutKind.Wide -> MachineShotsWideLayout(layout, state, onMachine, onShot, onBack, modifier)
        LayoutKind.Phone,
        LayoutKind.Stacked,
        LayoutKind.Tiny -> MachineShotsPhoneLayout(state, onMachine, onShot, onBack, modifier)
    }
}
