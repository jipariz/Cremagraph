package app.onlyshots.visualizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.model.Shot
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.visualizer_error_busy
import app.onlyshots.core.resources.visualizer_error_input
import app.onlyshots.core.resources.visualizer_error_not_a_shot
import app.onlyshots.core.resources.visualizer_error_not_found
import app.onlyshots.core.resources.visualizer_error_unreachable
import app.onlyshots.core.resources.visualizer_fetch
import app.onlyshots.core.resources.visualizer_fetching
import app.onlyshots.core.resources.visualizer_field
import app.onlyshots.core.resources.visualizer_hint
import app.onlyshots.core.resources.visualizer_placeholder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** A fetch from visualizer.coffee as the form shows it: running, or why the last one failed. */
data class VisualizerFetchState(val busy: Boolean = false, val error: UiText? = null)

/**
 * Fetches a shot from what the user typed, for a view model ([scope] its own): the Open screen's and the editor's,
 * which do different things with the shot ([fetch]'s `onShot`).
 */
class VisualizerFetcher(private val visualizer: Visualizer, private val scope: CoroutineScope) {
    val state: StateFlow<VisualizerFetchState>
        field = MutableStateFlow(VisualizerFetchState())

    private var job: Job? = null

    fun fetch(input: String, onShot: (Shot) -> Unit) {
        if (state.value.busy) return
        val ref = VisualizerRef.parse(input)
        if (ref == null) {
            state.value = VisualizerFetchState(error = Res.string.visualizer_error_input.text())
            return
        }
        state.value = VisualizerFetchState(busy = true)
        job = scope.launch {
            try {
                val shot = visualizer.fetch(ref)
                state.value = VisualizerFetchState()
                onShot(shot)
            } catch (e: VisualizerException) {
                state.value = VisualizerFetchState(error = e.error.text())
            }
        }
    }

    /** The form closed: a fetch still running is dropped, and its error forgotten. */
    fun reset() {
        job?.cancel()
        state.value = VisualizerFetchState()
    }
}

fun VisualizerError.text(): UiText =
    when (this) {
        VisualizerError.NOT_FOUND -> Res.string.visualizer_error_not_found.text()
        VisualizerError.BUSY -> Res.string.visualizer_error_busy.text()
        VisualizerError.UNREACHABLE -> Res.string.visualizer_error_unreachable.text()
        VisualizerError.NOT_A_SHOT -> Res.string.visualizer_error_not_a_shot.text()
    }

/**
 * The share code or link, how to get one, and "fetch shot"; the error under the field when the last try failed. Done on
 * the keyboard fetches too.
 */
@Composable
fun VisualizerFetchForm(state: VisualizerFetchState, onFetch: (String) -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Text(
            stringResource(Res.string.visualizer_hint),
            style = CgTheme.type.bodySmall,
            color = CgColors.Text.Secondary,
        )
        CgTextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(Res.string.visualizer_field),
            placeholder = stringResource(Res.string.visualizer_placeholder),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { onFetch(input) }),
            modifier = Modifier.fillMaxWidth().testTag(TestTags.VisualizerInput),
        )
        state.error?.let {
            Text(
                it.text(),
                style = CgTheme.type.bodySmall,
                color = CgColors.Status.Warning,
                modifier = Modifier.testTag(TestTags.VisualizerError),
            )
        }
        CgButton(
            text = stringResource(if (state.busy) Res.string.visualizer_fetching else Res.string.visualizer_fetch),
            onClick = { onFetch(input) },
            enabled = !state.busy && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag(TestTags.VisualizerFetch),
        )
    }
}
