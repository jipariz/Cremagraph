package app.onlyshots.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_zoom_lens
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The lenses as the camera app offers them: a pill of stops ("0.6", "1.0×", "2"), the one the picture is on lit with
 * the zoom itself. A tap goes to that lens; anything between is a pinch on the picture. Nothing for a single lens at
 * 1×; zoomed in on it, the zoom alone, a tap back to 1×.
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
                    Modifier.size(CgSize.Touch)
                        .clip(CircleShape)
                        .clickable { onZoom(stop) }
                        .semantics {
                            role = Role.Button
                            contentDescription = description
                            selected = on
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(if (on) ACTIVE_STOP else STOP)
                            .clip(CircleShape)
                            .background(if (on) Color.White else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (on) zoomLabel(state.zoom) else lensLabel(stop),
                            style = if (on) CgTheme.type.labelStrong else CgTheme.type.label,
                            color = if (on) CgColors.Text.OnLight else Color.White,
                        )
                    }
                }
            }
        }
    val pill = modifier.clip(CgShape.Pill).background(Color.Black.copy(alpha = 0.45f)).padding(2.dp)
    if (vertical) Column(pill, horizontalAlignment = Alignment.CenterHorizontally) { stops() }
    else Row(pill, verticalAlignment = Alignment.CenterVertically) { stops() }
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

private val STOP = 36.dp
private val ACTIVE_STOP = 40.dp

/**
 * The ring where the user tapped to focus, drawn while the camera settles and then fading to a dimmer one that stays as
 * long as focus and exposure are held there. [key] changes with every tap, so a new tap restarts it.
 */
@Composable
fun FocusRing(at: Offset, key: Int, modifier: Modifier = Modifier) {
    val scale = remember(key) { Animatable(1.4f) }
    val alpha = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        scale.animateTo(1f, tween(FOCUS_SETTLE_MS))
        alpha.animateTo(0.5f, tween(FOCUS_FADE_MS))
    }
    Canvas(modifier.fillMaxSize().alpha(alpha.value)) {
        val r = FOCUS_RING_RADIUS.dp.toPx() * scale.value
        drawCircle(FocusYellow, r, at, style = Stroke(2.dp.toPx()))
        drawCircle(FocusYellow, 2.5.dp.toPx(), at)
    }
}

/** The focus ring's radius in dp; the lock label sits just under it. */
internal const val FOCUS_RING_RADIUS = 32

/** The camera apps' focus colour, which reads on any picture. */
internal val FocusYellow = Color(0xFFFFD60A)
private const val FOCUS_SETTLE_MS = 250
private const val FOCUS_FADE_MS = 900
