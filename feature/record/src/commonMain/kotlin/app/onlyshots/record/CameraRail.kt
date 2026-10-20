package app.onlyshots.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgRoundButton
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_zoom
import app.onlyshots.core.resources.record_zoom_closed
import app.onlyshots.core.resources.record_zoom_open
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The camera rail: the zoom as a small "1×" button. Tapping it opens a short track above it (from the camera's widest
 * zoom to its longest); tapping it again while open goes back to 1×. A pinch on the picture ([reveal] changes) opens it
 * too, and it closes itself a moment after the last touch. Hidden while recording, so nothing changes mid-shot.
 */
@Composable
fun CameraRail(state: CameraState, onZoom: (Float) -> Unit, reveal: Int, modifier: Modifier = Modifier) {
    if (state.maxZoom <= state.minZoom) return
    val zoom by rememberUpdatedState(state.zoom)
    val change by rememberUpdatedState(onZoom)
    var open by remember { mutableStateOf(false) }
    // Every touch on the rail counts as activity, which restarts the timer that closes it.
    var touched by remember { mutableStateOf(0) }
    var firstReveal by remember { mutableStateOf(true) }
    LaunchedEffect(reveal) {
        if (firstReveal) firstReveal = false
        else {
            open = true
            touched++
        }
    }
    LaunchedEffect(open, touched) {
        if (!open) return@LaunchedEffect
        delay(ZOOM_HIDE_MS)
        open = false
    }
    // A fixed height with the button at the bottom, so the button stays put when the track opens above it.
    Column(
        modifier.height(ZOOM_TRACK_HEIGHT.dp + CgSpace.S6 + CgSize.Touch),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S6, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            val zoomWord = stringResource(Res.string.record_zoom)
            BoxWithConstraints(
                Modifier.width(ZOOM_TRACK_WIDTH.dp)
                    .height(ZOOM_TRACK_HEIGHT.dp)
                    .clip(CircleShape)
                    .background(CgColors.Surface.Scrim.copy(alpha = 0.55f))
                    .semantics {
                        contentDescription = zoomWord
                        progressBarRangeInfo = ProgressBarRangeInfo(state.zoom, state.minZoom..state.maxZoom)
                        setProgress { value ->
                            change(value)
                            true
                        }
                    }
                    .pointerInput(state.minZoom, state.maxZoom) {
                        val pad = THUMB.dp.toPx() / 2
                        fun zoomAtY(y: Float): Float {
                            // Top is the longest zoom.
                            val fraction = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                            return zoomAt(fraction, state.minZoom, state.maxZoom)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            touched++
                            change(zoomAtY(down.position.y))
                            while (true) {
                                val ev = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!ev.pressed) break
                                ev.consume()
                                touched++
                                change(zoomAtY(ev.position.y))
                            }
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                val travel = maxHeight - THUMB.dp
                // 1×, so the main lens is easy to find again.
                if (state.minZoom < 1f) {
                    val one = zoomFraction(1f, state.minZoom, state.maxZoom)
                    Box(
                        Modifier.offset(y = travel * (1f - one) + THUMB.dp / 2 - 1.dp)
                            .size(8.dp, 2.dp)
                            .background(CgColors.Text.Dim)
                    )
                }
                Box(
                    Modifier.offset(y = travel * (1f - zoomFraction(zoom, state.minZoom, state.maxZoom)))
                        .size(THUMB.dp)
                        .clip(CircleShape)
                        .background(CgColors.Text.Primary)
                )
            }
        }
        CgRoundButton(
            stringResource(
                if (open) Res.string.record_zoom_open else Res.string.record_zoom_closed,
                zoomLabel(state.zoom),
            ),
            onClick = {
                if (open) onZoom(1f) else open = true
                touched++
            },
        ) {
            Text(zoomLabel(state.zoom), style = CgTheme.type.label)
        }
    }
}

/**
 * Where [zoom] sits on the track, 0 at [min] and 1 at [max]. Logarithmic, as zoom is felt: 1× to 2× takes as much track
 * as 4× to 8×.
 */
fun zoomFraction(zoom: Float, min: Float, max: Float): Float =
    if (max <= min) 0f else (ln(zoom.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0f, 1f)

/** The zoom at [fraction] of the track, the inverse of [zoomFraction]. */
fun zoomAt(fraction: Float, min: Float, max: Float): Float =
    if (max <= min) min else (min * (max / min).pow(fraction.coerceIn(0f, 1f))).coerceIn(min, max)

/** "0.5×", "1×", "2.4×": one decimal, none when it is a whole number. */
fun zoomLabel(zoom: Float): String {
    val tenths = (zoom * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}×" else "${tenths / 10}.${tenths % 10}×"
}

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

private const val ZOOM_TRACK_HEIGHT = 112
private const val ZOOM_TRACK_WIDTH = 28
private const val THUMB = 18
private const val ZOOM_HIDE_MS = 2500L
/** The focus ring's radius in dp; the lock label sits just under it. */
internal const val FOCUS_RING_RADIUS = 32

/** The camera apps' focus colour, which reads on any picture. */
internal val FocusYellow = Color(0xFFFFD60A)
private const val FOCUS_SETTLE_MS = 250
private const val FOCUS_FADE_MS = 900
