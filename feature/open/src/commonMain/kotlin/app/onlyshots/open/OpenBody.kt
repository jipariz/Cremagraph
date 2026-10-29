package app.onlyshots.open

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout

/**
 * The start from plain state: the two steps, and the button that opens the editor once both are picked. Who shows it
 * ([entry]) picks the page: the phone app's (a column on a phone, panes on a tablet or a fold), or the desktop's and
 * the web's "Open Screen" design (its own compact page in a phone's browser).
 */
@Composable
fun OpenBody(
    ui: OpenUi,
    entry: OpenEntry,
    modifier: Modifier = Modifier,
    actions: OpenActions = OpenActions.None,
) {
    val layout = LocalWindowLayout.current
    when (entry) {
        is OpenEntry.PhoneApp ->
            if (layout.kind == LayoutKind.Wide) OpenWideLayout(ui, actions, entry.onBack, layout, modifier)
            else OpenPhoneLayout(ui, actions, entry.onBack, modifier)
        is OpenEntry.Desk -> {
            val phoneStores = entry.phoneStores(layout)
            if (phoneStores != null) {
                OpenMobileWebLayout(ui, actions, phoneStores, entry.host?.videosNote, modifier)
            } else {
                OpenDeskLayout(ui, actions, entry, modifier)
            }
        }
    }
}
