package app.onlyshots.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.FormatOption
import app.onlyshots.core.designsystem.component.FormatPicker
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_done
import app.onlyshots.core.resources.edit_loading
import app.onlyshots.label
import org.jetbrains.compose.resources.stringResource

/**
 * The phone layout, as the web editor's: chips for the shot and the export along the top, the picture filling the
 * screen, and the timeline band under it; everything else in sheets that leave the picture in view. Until the [video]
 * loads, only the bar and a word that it is loading.
 */
@Composable
internal fun EditPhoneLayout(
    ui: EditUiState,
    video: VideoItem?,
    player: PlayerState,
    local: EditLocalState,
    chrome: EditChrome,
    actions: EditActions,
    onBack: () -> Unit,
    onExported: () -> Unit,
    slots: EditSlots,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            EditTopBar(video, player, onBack = onBack, onOpenSheet = actions::openSheet)
            if (video == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.edit_loading), style = CgTheme.type.body, color = CgColors.Text.Dim)
                }
                return@Column
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                slots.picture(Modifier.fillMaxSize())
                MessageBanner(ui.message, actions::clearMessage, Modifier.align(Alignment.TopCenter))
            }
            if (local.reframing) {
                FramingKeys(video, actions, onDone = { local.reframe(false) })
            } else {
                slots.timeBand()
            }
        }
        if (video != null) {
            // The export's own screen stands in for its sheet while it runs and until it is seen to.
            EditBottomSheet(chrome.sheet, ui, actions, scrim = true, content = slots.sheet)
            EditExportOverlay(chrome.exportOverlay, ui, video, actions, onExported, fullScreen = true)
        }
    }
}

/** Framing: the formats under the picture, and "done" at the bottom right, where the timeline was. */
@Composable
private fun FramingKeys(video: VideoItem, actions: EditActions, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        FormatPicker(FORMAT_OPTIONS, formatIndex(video.format), onPick = { actions.setFormat(formatAt(it)) })
        Box(Modifier.fillMaxWidth().padding(CgSpace.S16), contentAlignment = Alignment.CenterEnd) {
            CgButton(
                stringResource(Res.string.edit_done),
                onClick = onDone,
                modifier = Modifier.widthIn(min = DONE_MIN_WIDTH).testTag(TestTags.EditFrame),
            )
        }
    }
}

private val DONE_MIN_WIDTH = 112.dp

/** The formats as the format pickers offer them, in [formatIndex]'s order. */
internal val FORMAT_OPTIONS = VideoFormat.entries.map { FormatOption(it.label, it.ratio) }
