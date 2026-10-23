package app.onlyshots.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_open_the_phone
import org.jetbrains.compose.resources.stringResource

/** A flip's cover screen: there is no room to edit, only to say so. */
@Composable
internal fun EditTinyLayout(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        BackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(CgSpace.S16))
        Text(
            stringResource(Res.string.edit_open_the_phone),
            style = CgTheme.type.screenTitle,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.align(Alignment.Center).padding(start = CgSpace.S24, end = CgSpace.S24, bottom = TEXT_LIFT),
        )
    }
}

/** The words sit a little above the middle, where the eye lands on a cover screen. */
private val TEXT_LIFT = 76.dp
