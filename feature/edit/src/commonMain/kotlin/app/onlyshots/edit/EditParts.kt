package app.onlyshots.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_done
import app.onlyshots.core.resources.edit_graph_start
import app.onlyshots.core.resources.edit_match_by_clock
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The editor's small parts that more than one place draws: the shot in a line, the framing key, and the shot start's
 * lock and clock keys.
 */

/** The bar in the machine's colour beside a shot's words: as tall as two lines of them. */
private val SHOT_BAR_WIDTH = 4.dp
internal val SHOT_BAR_HEIGHT = 36.dp

/**
 * A shot in one line: a bar in its machine's [color] beside its [title] and, under it, [subtitle]; [trailing] after
 * them (a "change" key).
 */
@Composable
internal fun ShotSummaryRow(
    color: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    barHeight: Dp = SHOT_BAR_HEIGHT,
    titleStyle: TextStyle = CgTheme.type.label,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = SHOT_BAR_WIDTH, height = barHeight).background(color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.caps(), style = titleStyle)
            Text(subtitle, style = CgTheme.type.data, color = CgColors.Text.Secondary)
        }
        trailing()
    }
}

/** The key that starts framing the picture ([offLabel]) and, while it is framed, says "done". */
@Composable
internal fun FrameToggle(
    reframing: Boolean,
    onReframing: (Boolean) -> Unit,
    offLabel: StringResource,
    modifier: Modifier = Modifier,
    /** The frame icon before the words while not framing. */
    icon: Boolean = false,
    overVideo: Boolean = false,
    fill: Boolean = false,
) {
    Pill(
        if (reframing) stringResource(Res.string.edit_done) else stringResource(offLabel),
        onClick = { onReframing(!reframing) },
        light = reframing,
        icon = if (icon && !reframing) CgIcons.Frame else null,
        overVideo = overVideo,
        fill = fill,
        modifier = modifier,
    )
}

/** A recording's locked shot start, and the key that unlocks it for moving by hand. */
@Composable
internal fun LockedStartPill(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Pill(stringResource(Res.string.edit_graph_start), onClick = onUnlock, icon = CgIcons.Lock, modifier = modifier)
}

/**
 * What can be done about where the shot starts: a locked start unlocked (the machine or scale may have started the take
 * a little early or late), or the shot lined up in one tap where the camera's clock and the machine's agree.
 */
@Composable
internal fun ShotStartPill(
    video: VideoItem,
    onUnlock: () -> Unit,
    onMatchByClock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        video.syncLocked -> LockedStartPill(onUnlock, modifier)
        video.shotStartByClock() != null ->
            Pill(stringResource(Res.string.edit_match_by_clock), onClick = onMatchByClock, modifier = modifier)
    }
}
