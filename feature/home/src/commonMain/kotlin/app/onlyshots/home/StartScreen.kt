package app.onlyshots.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_about
import app.onlyshots.layout.FoldPage
import app.onlyshots.layout.LocalWindowLayout
import org.jetbrains.compose.resources.stringResource

/**
 * The phones' first screen: record a shot live (Home, then the camera) or compose a video from one already filmed (pick
 * it and its shot file, then the editor). The ? opens the help.
 */
@Composable
fun StartScreen(onRecord: () -> Unit, onEdit: () -> Unit, onPrivacy: (() -> Unit)? = null) {
    var help by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.home_about)
    val content: @Composable () -> Unit = {
        HelpContent(
            onPrivacy =
                onPrivacy?.let { open ->
                    {
                        help = false
                        open()
                    }
                }
        )
    }
    // On a book foldable the help takes the right page, in place of the choices; elsewhere it is a sheet.
    val fold = LocalWindowLayout.current.fold
    StartBody(
        onRecord = onRecord,
        onEdit = onEdit,
        onHelp = { help = true },
        helpPage =
            if (help && fold) {
                {
                    FoldPage(title, onDismiss = { help = false }) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(CgSpace.S16)) {
                            content()
                        }
                    }
                }
            } else {
                null
            },
    )
    if (help && !fold) CgSheet(title = title, onDismiss = { help = false }) { content() }
}
