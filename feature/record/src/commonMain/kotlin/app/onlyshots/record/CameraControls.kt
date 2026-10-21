package app.onlyshots.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_zoom_lens
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The lenses as the camera app offers them: a strip of stops ("0.6", "1.0×", "2") in a hairline frame, the one the
 * picture is on filled bone and showing the zoom itself. A tap goes to that lens; anything between is a pinch on the
 * picture. Nothing for a single lens at 1×; zoomed in on it, the zoom alone, a tap back to 1×.
 */
@Composable
fun LensPicker(state: CameraState, onZoom: (Float) -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val lenses = state.lenses
    if (lenses.size < 2 && activeLens(state.zoom, lenses).let { lenses[it] == state.zoom }) return
    val active = activeLens(state.zoom, lenses)
    val stops =
        @Composable {
            lenses.forEachIndexed { i, stop ->
                val on = i == active
                val description = stringResource(Res.string.record_zoom_lens, zoomLabel(stop))
                Box(
                    Modifier.size(width = STOP_WIDTH, height = STOP_HEIGHT)
                        .background(if (on) CgColors.Brand.Bone else Color.Transparent)
                        .clickable { onZoom(stop) }
                        .semantics {
                            role = Role.Button
                            contentDescription = description
                            selected = on
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (on) zoomLabel(state.zoom) else lensLabel(stop),
                        style = CgTheme.type.labelStrong,
                        color = if (on) CgColors.Text.OnLight else CgColors.Text.Secondary,
                    )
                }
            }
        }
    val strip =
        modifier
            .background(CgColors.Surface.Base.copy(alpha = 0.85f))
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .padding(CgStroke.Hairline)
    if (vertical) Column(strip, horizontalAlignment = Alignment.CenterHorizontally) { stops() }
    else Row(strip, verticalAlignment = Alignment.CenterVertically) { stops() }
}

/** Which of [lenses] (ascending) the picture is on at [zoom]: the longest one reached, a pinch past it included. */
fun activeLens(zoom: Float, lenses: List<Float>): Int = lenses.indexOfLast { zoom >= it - 0.05f }.coerceAtLeast(0)

/** A lens as its stop names it: "0.6", "1", "2.5". */
fun lensLabel(stop: Float): String {
    val tenths = (stop * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/** The zoom the picture is at, as the lit stop shows it: "1.0×", "2.4×". */
fun zoomLabel(zoom: Float): String {
    val tenths = (zoom * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}×"
}

private val STOP_WIDTH = 52.dp

/** A lens stop's height: the canvas's 40, a strip low enough to sit over the shutter bar. */
private val STOP_HEIGHT = 40.dp

/**
 * The square where the user tapped to focus: it appears at its size, blinks twice while the camera settles, like a
 * lamp, then dims to the one that stays as long as focus and exposure are held there. [key] changes with every tap, so
 * a new tap restarts it.
 */
@Composable
fun FocusRing(at: Offset, key: Int, modifier: Modifier = Modifier) {
    var alpha by remember(key) { mutableFloatStateOf(1f) }
    LaunchedEffect(key) {
        repeat(FOCUS_BLINKS) {
            delay(FOCUS_BLINK_MS)
            alpha = 0f
            delay(FOCUS_BLINK_MS)
            alpha = 1f
        }
        delay(FOCUS_BLINK_MS)
        alpha = 0.5f
    }
    Canvas(modifier.fillMaxSize().alpha(alpha)) {
        val half = FOCUS_RING_RADIUS.dp.toPx()
        drawRect(
            CgColors.Status.Warning,
            Offset(at.x - half, at.y - half),
            Size(half * 2, half * 2),
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

/** Half the focus square's side in dp; the lock label sits just under it. */
internal const val FOCUS_RING_RADIUS = 28
private const val FOCUS_BLINKS = 2
private const val FOCUS_BLINK_MS = 90L
