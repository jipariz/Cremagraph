package app.onlyshots.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.topRule
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_find_start
import app.onlyshots.core.resources.edit_fit
import app.onlyshots.core.resources.edit_start_here
import app.onlyshots.core.resources.edit_step_back
import app.onlyshots.core.resources.edit_step_forward
import app.onlyshots.core.resources.edit_zoom_in
import org.jetbrains.compose.resources.stringResource

/** The timeline and its tools under it: place the shot's start, find it by ear, step a frame, zoom. */
@Composable
internal fun EditTimeBand(
    ui: EditUiState,
    video: VideoItem,
    player: PlayerState,
    local: EditLocalState,
    actions: EditActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().topRule().background(CgColors.Surface.Base).padding(bottom = CgSpace.S8)) {
        EditTimeline(
            item = video,
            playheadUs = { player.positionUs },
            zoomFactor = local.zoom,
            thumbnails = ui.thumbnails,
            loading = ui.stripLoading,
            waveform = ui.waveform,
            // The pump in the sound only helps line a shot up by hand: a recording the machine or scale started is
            // already in step, and a line its flag does not sit on would only confuse.
            pumpUs = ui.pumpUs.takeIf { !video.syncLocked },
            actions =
                TimelineActions(
                    seek = { player.seekPaused(it) },
                    trim = { a, b, push -> actions.trim(a, b, push) },
                    shotStart = { us, push -> actions.setShotStart(us, push) },
                    zoom = { local.zoom = it },
                ),
        )
        TimeTools(video, player, local, actions)
    }
}

/**
 * The keys under the timeline. Too narrow for them all with a gap between the two groups (a book foldable's page), the
 * row scrolls instead: this measures the row itself, so [BoxWithConstraints] only fits the content here.
 */
@Composable
private fun TimeTools(
    video: VideoItem,
    player: PlayerState,
    local: EditLocalState,
    actions: EditActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < TOOLS_SPREAD_MIN_WIDTH
        Row(
            Modifier.fillMaxWidth()
                .then(if (narrow) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = CgSpace.S12),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lining the shot up by hand; a recording the machine or scale started is in step already, until its start
            // is unlocked.
            if (video.syncLocked && video.shot != null) {
                LockedStartPill(onUnlock = actions::unlockShotStart)
            } else if (!video.syncLocked) {
                val movable = video.shot != null
                Pill(
                    stringResource(Res.string.edit_start_here),
                    onClick = { actions.setShotStart(player.positionUs, push = true) },
                    enabled = movable,
                    icon = CgIcons.Flag,
                )
                Pill(stringResource(Res.string.edit_find_start), onClick = actions::findStart, enabled = movable)
            }
            if (!narrow) Spacer(Modifier.weight(1f))
            Pill(
                "",
                onClick = { player.seekPaused(TimelineMath.stepFrame(video, player.positionUs, -1)) },
                icon = CgIcons.ChevronLeft,
                iconDescription = stringResource(Res.string.edit_step_back),
            )
            Pill(
                "",
                onClick = { player.seekPaused(TimelineMath.stepFrame(video, player.positionUs, 1)) },
                icon = CgIcons.ChevronRight,
                iconDescription = stringResource(Res.string.edit_step_forward),
            )
            Pill(
                if (local.zoom == null) "" else stringResource(Res.string.edit_fit),
                onClick = local::toggleZoom,
                icon = if (local.zoom == null) CgIcons.Plus else null,
                iconDescription = stringResource(Res.string.edit_zoom_in),
            )
        }
    }
}

/** A seek from the timeline or its keys stops playback first, so the frame landed on stays. */
private fun PlayerState.seekPaused(us: Long) {
    playing = false
    seek(us)
}

/**
 * Narrower than this, the timeline's keys scroll rather than spread. It is the row's own width that matters, not the
 * window's, so this is a content fit and not one of the layout breakpoints.
 */
private val TOOLS_SPREAD_MIN_WIDTH = 400.dp
