package app.onlyshots.record

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_ready
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.stringResource

/**
 * A flip's cover screen or a sliver of split screen: the picture ([pane], drawn [PaneChrome.Bare]), the clock over it
 * and the button under it, nothing else.
 */
@Composable
internal fun RecordTinyLayout(
    take: TakeInputs,
    pane: @Composable (Modifier, PaneChrome) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().safeDrawingPadding()) {
        pane(Modifier.fillMaxSize(), PaneChrome.Bare)
        TinyControls(take, Modifier.fillMaxSize())
    }
}

/** The clock over the picture and the record button under it. */
@Composable
private fun TinyControls(take: TakeInputs, modifier: Modifier = Modifier) {
    val state = take.state
    Box(modifier) {
        // Read while drawing: the change of colour redraws the chip, it does not recompose it.
        val ground =
            animateColorAsState(
                if (state.recording) CgColors.Status.Record else CgColors.Surface.Raised,
                CgMotion.medium(),
            )
        Text(
            if (state.recording) state.status.text() else stringResource(Res.string.record_ready),
            style = CgTheme.type.label,
            color = CgColors.Text.Primary,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .padding(top = CgSpace.S24)
                    .drawBehind { drawRect(ground.value) }
                    .padding(horizontal = CgSpace.S14, vertical = CgSpace.S6),
        )
        RecordKey(take, Modifier.align(Alignment.BottomCenter).padding(bottom = CgSpace.S24))
    }
}
