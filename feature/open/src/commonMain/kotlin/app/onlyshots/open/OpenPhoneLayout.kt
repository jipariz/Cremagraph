package app.onlyshots.open

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.open_header_edit
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.stringResource

/**
 * The phone app's Open on a phone (Open-Phone, Open-Ready): a back header, the two steps, the picked video beside its
 * name, and "open editor" across the bottom. A column, centred where the window is wider.
 */
@Composable
internal fun OpenPhoneLayout(
    ui: OpenUi,
    actions: OpenActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ColumnMaxWidth).fillMaxSize()) {
            HeaderBack(
                stringResource(Res.string.open_header_edit),
                onBack = onBack,
                modifier = Modifier.padding(vertical = CgSpace.S8),
            )
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CgSpace.S16)
                    .padding(top = CgSpace.S8),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
            ) {
                AppSteps(ui, StepWords.PhoneApp, actions)
                ui.problem?.let { StatusLine(it.text()) }
                ui.picked?.let { PickedVideo(it, ui.pickedFile) }
            }
            OpenEditorKey(
                ready = ui.ready,
                onClick = actions::openEditor,
                fill = true,
                modifier = Modifier.padding(CgSpace.S16),
            )
        }
    }
}

/**
 * The picked video on the phones, from Open-Ready: a frame of it in 9:16 with its length on it, and beside it, its name
 * and size.
 */
@Composable
private fun PickedVideo(picked: VideoItem, file: PickedFile?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(CgSpace.S16), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(PickedFrameWidth, PickedFrameHeight).border(CgStroke.Hairline, CgColors.Line.Hairline)) {
            VideoTile(file?.thumb, Modifier.fillMaxSize().padding(CgStroke.Hairline))
            LengthTag(picked, Modifier.align(Alignment.BottomStart))
        }
        VideoFacts(file, Modifier.padding(bottom = CgSpace.S4))
    }
}

/** The column stops growing here on a wider window that still gets the phone's layout. */
private val ColumnMaxWidth = 560.dp

/** The picked video's frame, 9:16 at about half the phone's width. */
private val PickedFrameWidth = 158.dp
private val PickedFrameHeight = 280.dp
