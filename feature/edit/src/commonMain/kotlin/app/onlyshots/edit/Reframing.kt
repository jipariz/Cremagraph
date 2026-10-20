package app.onlyshots.edit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import app.onlyshots.core.model.Reframe
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Draws the picture through [reframe]: scaled about the frame's centre and moved so the reframe's centre lands there,
 * the same mapping as [Reframe.map] and the exporters' matrix. The parent clips.
 */
fun Modifier.reframed(reframe: Reframe): Modifier =
    if (reframe.isIdentity) this
    else
        graphicsLayer {
            scaleX = reframe.zoom
            scaleY = reframe.zoom
            translationX = (0.5f - reframe.x) * reframe.zoom * size.width
            translationY = (0.5f - reframe.y) * reframe.zoom * size.height
        }

/**
 * The whole picture of width / height [aspect], cover-fitted around the frame it is placed in and centred on it: what
 * reaches past the frame is drawn too, so [reframed] can move it into view. The frame's parent clips. Without a known
 * shape, the picture fills the frame.
 */
fun Modifier.coverOf(aspect: Float?): Modifier =
    if (aspect == null) fillMaxSize()
    else
        layout { measurable, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val coverW = maxOf(w.toFloat(), h * aspect).roundToInt()
            val coverH = maxOf(h.toFloat(), w / aspect).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(coverW, coverH))
            layout(w, h) { placeable.place((w - coverW) / 2, (h - coverH) / 2) }
        }

/**
 * Drag to move the picture, pinch to zoom it, or the scroll wheel on a desktop or in a browser. One gesture is one undo
 * step: the first change of a touch pushes, the rest don't; wheel ticks close together count as one.
 */
fun Modifier.reframeGestures(
    current: () -> Reframe,
    onChange: (push: Boolean, (Reframe) -> Reframe) -> Unit,
): Modifier =
    pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var first = true
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    if (event.changes.none { it.positionChanged() }) continue
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val z = current().zoom
                    onChange(first) { r ->
                        r.copy(
                            zoom = r.zoom * zoom,
                            x = r.x - pan.x / (size.width * z),
                            y = r.y - pan.y / (size.height * z),
                        )
                    }
                    first = false
                    event.changes.forEach { it.consume() }
                }
            }
        }
        .pointerInput(Unit) {
            var lastWheel: TimeSource.Monotonic.ValueTimeMark? = null
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type != PointerEventType.Scroll) continue
                    val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    if (dy == 0f) continue
                    val push = lastWheel.let { it == null || it.elapsedNow().inWholeMilliseconds > WHEEL_STEP_MS }
                    lastWheel = TimeSource.Monotonic.markNow()
                    onChange(push) { r -> r.copy(zoom = r.zoom * exp(-dy * WHEEL_ZOOM)) }
                    event.changes.forEach { it.consume() }
                }
            }
        }

private const val WHEEL_STEP_MS = 400
private const val WHEEL_ZOOM = 0.1f
