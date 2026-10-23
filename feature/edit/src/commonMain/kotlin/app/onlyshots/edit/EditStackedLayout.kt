package app.onlyshots.edit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.TopChip
import app.onlyshots.core.designsystem.component.TwoPane
import app.onlyshots.core.designsystem.component.topRule
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_add_shot
import app.onlyshots.core.resources.edit_data
import app.onlyshots.core.resources.edit_frame
import app.onlyshots.core.resources.edit_of_duration
import app.onlyshots.core.resources.edit_opacity
import app.onlyshots.core.resources.edit_pause
import app.onlyshots.core.resources.edit_play
import app.onlyshots.core.resources.edit_style_named
import app.onlyshots.core.resources.text
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.machineLabel
import org.jetbrains.compose.resources.stringResource

/**
 * On its side (a book in tabletop, a flip in flex mode): the player in the upper half, the timeline and the tools on
 * the lower one. Sheets rise over both halves, and the export takes the whole screen, as on a phone.
 */
@Composable
internal fun EditStackedLayout(
    ui: EditUiState,
    video: VideoItem,
    player: PlayerState,
    local: EditLocalState,
    chrome: EditChrome,
    actions: EditActions,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onExported: () -> Unit,
    slots: EditSlots,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWindowLayout.current
    Box(modifier) {
        TwoPane(
            vertical = true,
            split = layout.splitDp.dp,
            hinge = layout.hingeDp.dp,
            first = {
                Box(Modifier.fillMaxSize()) {
                    PlayerHalf(
                        video = video,
                        player = player,
                        reframing = local.reframing,
                        onReframing = local::reframe,
                        onBack = onBack,
                        picture = slots.picture,
                    )
                    MessageBanner(ui.message, actions::clearMessage, Modifier.align(Alignment.TopCenter))
                }
            },
            second = {
                Column(Modifier.fillMaxSize()) {
                    slots.timeBand()
                    ToolChips(ui, video, actions, onExport = onExport)
                }
            },
        )
        EditBottomSheet(chrome.sheet, ui, actions, scrim = false, content = slots.sheet)
        EditExportOverlay(chrome.exportOverlay, ui, video, actions, onExported, fullScreen = true)
    }
}

/**
 * The upper half on its side: back, what is edited, the sound on the left; the picture in its shape in the middle;
 * framing, the time and play on the right.
 */
@Composable
private fun PlayerHalf(
    video: VideoItem,
    player: PlayerState,
    reframing: Boolean,
    onReframing: (Boolean) -> Unit,
    onBack: () -> Unit,
    picture: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().padding(CgSpace.S12), horizontalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
            BackButton(onClick = onBack)
            Text(editTitle(video).text().caps(), style = CgTheme.type.screenTitle, maxLines = 2)
            val shot = video.shot
            if (shot != null) {
                ShotSummaryRow(
                    color = machineColor(shot.source),
                    title = "${shot.profileName} · ${machineLabel(shot.source)}",
                    subtitle = shotDetail(video)?.text().orEmpty(),
                    barHeight = SHOT_BAR_HEIGHT_HALF,
                    modifier =
                        Modifier.fillMaxWidth()
                            .border(CgStroke.Hairline, CgColors.Line.Hairline)
                            .padding(horizontal = CgSpace.S12, vertical = 10.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            SoundToggle(video, player)
        }
        picture(Modifier.fillMaxHeight().aspectRatio(video.format.ratio, matchHeightConstraintsFirst = true))
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
            horizontalAlignment = Alignment.End,
        ) {
            FrameToggle(reframing, onReframing, offLabel = Res.string.edit_frame)
            Spacer(Modifier.weight(1f))
            PlayheadClock(player)
            Text(
                stringResource(Res.string.edit_of_duration, clock(video.durationUs)),
                style = CgTheme.type.caption,
                color = CgColors.Text.Secondary,
            )
            PlayKey(player)
        }
    }
}

/** The half's shot bar is a little shorter than the panel's: the half has less height to give. */
private val SHOT_BAR_HEIGHT_HALF = 32.dp

/** Play or pause, large, for a thumb on the half standing up. */
@Composable
private fun PlayKey(player: PlayerState, modifier: Modifier = Modifier) {
    val playLabel = if (player.playing) stringResource(Res.string.edit_pause) else stringResource(Res.string.edit_play)
    Box(
        modifier
            .size(PLAY_KEY_SIZE)
            .border(CgStroke.Hairline, CgColors.Brand.Bone)
            .clickable(role = Role.Button, onClick = player::toggle)
            .semantics { contentDescription = playLabel },
        contentAlignment = Alignment.Center,
    ) {
        CgIcon(if (player.playing) CgIcons.Pause else CgIcons.Play, size = PLAY_ICON_SIZE)
    }
}

private val PLAY_KEY_SIZE = 64.dp
private val PLAY_ICON_SIZE = 28.dp

/**
 * Where the playhead is, as a clock: its own scope, changing only when the shown time does (tenths), so playback does
 * not recompose the half of the screen around it every frame.
 */
@Composable
private fun PlayheadClock(player: PlayerState, modifier: Modifier = Modifier) {
    val shown by remember(player) { derivedStateOf { clock(player.positionUs) } }
    Text(shown, style = CgTheme.type.pivot, modifier = modifier)
}

/** The tools under the timeline on its side, one chip each: the graph's style, data, text and opacity, and export. */
@Composable
private fun ToolChips(
    ui: EditUiState,
    video: VideoItem,
    actions: EditActions,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = CgSpace.S24)
            .topRule()
            .horizontalScroll(rememberScrollState())
            .padding(top = CgSpace.S12, bottom = CgSpace.S24),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (video.shot != null) {
            Pill(
                stringResource(Res.string.edit_style_named, ui.style?.name ?: video.overlay.styleId),
                onClick = { actions.openSheet(EditSheet.STYLE) },
            )
            Pill(stringResource(Res.string.edit_data), onClick = { actions.openSheet(EditSheet.DATA) })
            Pill(
                stringResource(Res.string.edit_opacity, "${opacityPercent(video.overlay.opacity)}"),
                onClick = { actions.overlay { it.withOpacity(nextOpacity(it.opacity)) } },
            )
        } else {
            Pill(stringResource(Res.string.edit_add_shot), onClick = { actions.openSheet(EditSheet.SHOT) })
        }
        TopChip(stringResource(LocalExportWording.current.save), onClick = onExport, primary = true)
    }
}
