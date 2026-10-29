package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.component.BarIcon
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.designsystem.component.LocalNativeChrome
import app.onlyshots.core.designsystem.component.NativeBarItem
import app.onlyshots.core.designsystem.component.NativeItems
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_about
import app.onlyshots.core.resources.privacy_title
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
    // Where the bars are native (iPhone Duo) help and privacy are its items, not keys on the screen.
    val privacyTitle = stringResource(Res.string.privacy_title)
    NativeItems(
        buildList {
            add(NativeBarItem(TestTags.StartHelp, title, BarIcon.Help, group = 1))
            if (onPrivacy != null) add(NativeBarItem(TestTags.HelpPrivacy, privacyTitle, BarIcon.Privacy, group = 1))
        }
    ) { id ->
        if (id == TestTags.StartHelp) help = true else onPrivacy?.invoke()
    }
    // On a book foldable the help takes the right page, in place of the choices; elsewhere it is a sheet. Where the
    // bars are native it is a page closed with the bar's back key, as Privacy is: a sheet's done key would sit under
    // the system's column.
    val fold = LocalWindowLayout.current.fold
    if (help && LocalNativeChrome.current != null) {
        val page: @Composable (Modifier) -> Unit = { modifier -> HelpPage(title, { help = false }, modifier, content) }
        if (!fold) {
            page(Modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding())
            return
        }
        StartBody(onRecord = onRecord, onEdit = onEdit, helpPage = { page(Modifier.fillMaxSize()) })
        return
    }
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
