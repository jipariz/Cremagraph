package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_timeline_description
import app.onlyshots.core.resources.core_timeline_earlier
import app.onlyshots.core.resources.core_timeline_later
import app.onlyshots.core.resources.core_timeline_playhead
import app.onlyshots.core.resources.core_timeline_trim_end
import app.onlyshots.core.resources.core_timeline_trim_start
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Figma "Timeline": the clip, the part outside the trim dimmed, two trim handles, the playhead and the shot-start flag
 * (a lock glyph when the recording is synced and the flag cannot move).
 *
 * Gestures belong to the caller: it owns the item and the undo stack.
 */
@Composable
fun Timeline(
    durationUs: Long,
    trimInUs: Long,
    trimOutUs: Long,
    playheadUs: Long,
    shotStartUs: Long?,
    shotStartLocked: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    onStep: ((TimelineTarget, forward: Boolean) -> Unit)? = null,
) {
    // Dragging a handle is not operable with a screen reader, so each one also steps by action.
    val description =
        stringResource(
            Res.string.core_timeline_description,
            seconds(trimInUs).toString(),
            seconds(trimOutUs).toString(),
            seconds(durationUs).toString(),
            seconds(playheadUs).toString(),
        )
    val actionLabels =
        TimelineTarget.entries.associateWith { target ->
            val name = stringResource(target.label)
            listOf(
                stringResource(Res.string.core_timeline_later, name),
                stringResource(Res.string.core_timeline_earlier, name),
            )
        }
    val described =
        if (onStep == null) modifier
        else
            modifier.semantics(mergeDescendants = true) {
                contentDescription = description
                customActions =
                    TimelineTarget.entries.flatMap { target ->
                        listOf(true, false).mapIndexed { i, forward ->
                            CustomAccessibilityAction(actionLabels.getValue(target)[i]) {
                                onStep(target, forward)
                                true
                            }
                        }
                    }
            }
    Box(described.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) {
            fun x(us: Long) = if (durationUs <= 0) 0f else (us.toFloat() / durationUs) * size.width
            drawRect(CgColors.Surface.Raised)
            val inX = x(trimInUs)
            val outX = x(trimOutUs)
            // Outside the trim: dimmed, so what will be exported is obvious.
            drawRect(CgColors.Surface.Scrim.copy(alpha = 0.6f), size = Size(inX, size.height))
            drawRect(
                CgColors.Surface.Scrim.copy(alpha = 0.6f),
                topLeft = Offset(outX, 0f),
                size = Size(size.width - outX, size.height),
            )
            // The hairline frame around the clip.
            val line = CgStroke.Hairline.toPx()
            drawRect(CgColors.Line.Hairline, style = Stroke(line))
            val handle = 6.dp.toPx()
            drawRect(CgColors.Brand.Bone, topLeft = Offset(inX - handle / 2, 0f), size = Size(handle, size.height))
            drawRect(CgColors.Brand.Bone, topLeft = Offset(outX - handle / 2, 0f), size = Size(handle, size.height))
            shotStartUs?.let { us ->
                val fx = x(us)
                val color = if (shotStartLocked) CgColors.Text.Disabled else CgColors.Status.Success
                drawRect(color, topLeft = Offset(fx - line / 2, 0f), size = Size(line, size.height))
                drawRect(color, topLeft = Offset(fx, 0f), size = Size(10.dp.toPx(), 10.dp.toPx()))
            }
            val px = x(playheadUs)
            val rule = CgStroke.Rule.toPx()
            drawRect(CgColors.Brand.Signal, topLeft = Offset(px - rule / 2, 0f), size = Size(rule, size.height))
        }
    }
}

/** What a screen reader can step on the timeline. */
enum class TimelineTarget(val label: StringResource) {
    TRIM_IN(Res.string.core_timeline_trim_start),
    TRIM_OUT(Res.string.core_timeline_trim_end),
    PLAYHEAD(Res.string.core_timeline_playhead),
}

private fun seconds(us: Long): Long = us / 1_000_000
