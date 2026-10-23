package app.onlyshots.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.TopChip
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_add_shot
import app.onlyshots.core.resources.edit_export
import app.onlyshots.core.resources.edit_muted
import app.onlyshots.core.resources.edit_sound
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's top bar: back, the shot with its status dot filling the rest of the bar, the sound key and the orange
 * export. The reframe switch sits on the picture ([EditPicture]), so the bar keeps room for the shot's name.
 */
@Composable
internal fun EditTopBar(
    video: VideoItem?,
    player: PlayerState,
    onBack: () -> Unit,
    onOpenSheet: (EditSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().bottomRule().padding(CgSpace.S8),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack)
        TopChip(
            text = video?.shot?.profileName ?: stringResource(Res.string.edit_add_shot),
            onClick = { onOpenSheet(EditSheet.SHOT) },
            modifier = Modifier.weight(1f).testTag(TestTags.EditShotChip),
            dot = shotMark(video).color,
        )
        SoundToggle(video, player)
        TopChip(
            stringResource(Res.string.edit_export),
            onClick = { onOpenSheet(EditSheet.EXPORT) },
            modifier = Modifier.testTag(TestTags.EditExport),
            primary = true,
        )
    }
}

private val ShotMark.color: Color
    get() =
        when (this) {
            ShotMark.NONE -> CgColors.Text.Dim
            ShotMark.PLACED -> CgColors.Status.Live
            ShotMark.UNPLACED -> CgColors.Status.Highlight
        }

/**
 * The wide editor's top bar: back, what is being edited (the shot, else the file), and the sound. The shot, the frame
 * and the export are in the side panel; undo and redo are Cmd/Ctrl+Z and Shift+Cmd/Ctrl+Z.
 */
@Composable
internal fun WideTopBar(video: VideoItem, player: PlayerState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().bottomRule().padding(CgSpace.S8),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack)
        Text(
            editTitle(video).text().caps(),
            style = CgTheme.type.label,
            color = CgColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SoundToggle(video, player)
    }
}

/**
 * Sound on or off, for playback and the export alike; a video recorded without the microphone has nothing to mute, so
 * there is no key.
 */
@Composable
internal fun SoundToggle(video: VideoItem?, player: PlayerState, modifier: Modifier = Modifier) {
    if (video?.hasAudio != true) return
    // A top-bar key like the shot and export ones beside it, the same height.
    TopChip(
        if (player.muted) stringResource(Res.string.edit_muted) else stringResource(Res.string.edit_sound),
        onClick = { player.muted = !player.muted },
        modifier = modifier.testTag(TestTags.EditSound),
    )
}

/** A hairline along the bottom edge: under the top bars and the panel's parts. */
internal fun Modifier.bottomRule(): Modifier = drawBehind {
    val h = CgStroke.Hairline.toPx()
    drawRect(CgColors.Line.Hairline, Offset(0f, size.height - h), Size(size.width, h))
}
