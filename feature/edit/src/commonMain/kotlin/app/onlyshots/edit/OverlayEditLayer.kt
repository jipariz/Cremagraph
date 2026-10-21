package app.onlyshots.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySnap
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_graph_description
import app.onlyshots.core.resources.edit_graph_style_action
import app.onlyshots.core.resources.snap_bottom
import app.onlyshots.core.resources.snap_bottom_above_captions
import app.onlyshots.core.resources.snap_centre
import app.onlyshots.core.resources.snap_left
import app.onlyshots.core.resources.snap_middle
import app.onlyshots.core.resources.snap_right
import app.onlyshots.core.resources.snap_top
import app.onlyshots.core.resources.snap_top_below_header
import kotlin.math.sqrt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The graph made editable on a preview (record and edit), as in the web editor: drag inside to move it, drag a grip to
 * resize it (any of its four corners), tap it for its style. While it moves, the areas a sharing app covers are shaded
 * and it snaps to the lines around them, the middle and the side margins, with a guide and a tick. Preview only —
 * nothing here reaches the recording or the export.
 *
 * [selected] shows the outline, the four corner grips and the [toolbar]; on the record screen it is always on.
 *
 * The frame follows the finger, so drags are measured in root coordinates; a move keeps its own unsnapped rect, so a
 * snap lets go as soon as the finger is past the pull distance.
 */
@Composable
fun OverlayEditLayer(
    rect: NormRect,
    onChange: (rect: NormRect, push: Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    /** The frame's shape: its minimum graph size and where the graph snaps. */
    format: VideoFormat = VideoFormat.PORTRAIT_9_16,
    /**
     * The part of the frame the graph is kept in, where not all of it is on screen (the record screen's camera fills
     * the window, so the frame's edges are cut off); null for the whole frame.
     */
    bounds: NormRect? = null,
    toolbar: (@Composable () -> Unit)? = null,
    /** Told when a move or resize starts and ends, so the screen can clear its controls off the picture meanwhile. */
    onDragging: (Boolean) -> Unit = {},
) {
    val change by rememberUpdatedState(onChange)
    val tap by rememberUpdatedState(onTap)
    val current by rememberUpdatedState(rect)
    val inside by rememberUpdatedState(bounds)
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    val draggingChanged by rememberUpdatedState(onDragging)
    LaunchedEffect(dragging) { draggingChanged(dragging) }
    var snap by remember { mutableStateOf<OverlaySnap.Snapped?>(null) }
    val description = stringResource(Res.string.edit_graph_description)
    val styleAction = stringResource(Res.string.edit_graph_style_action)

    BoxWithConstraints(modifier) {
        val frameW = constraints.maxWidth.toFloat()
        val frameH = constraints.maxHeight.toFloat()
        val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        // The outline and grips fade in on selection and thicken under the finger, instead of popping.
        val chrome by animateFloatAsState(if (selected || dragging) 1f else 0f, CgMotion.short(), label = "chrome")
        val grab by animateFloatAsState(if (dragging) 1f else 0f, CgMotion.short(), label = "grab")

        // While it moves, the bands a sharing app covers (its header, its captions) are hatched.
        AnimatedVisibility(dragging, enter = fadeIn(CgMotion.short()), exit = fadeOut(CgMotion.short())) { SafeBands() }

        // The guides fade in where the graph snapped and out when it lets go.
        var lastSnap by remember { mutableStateOf<OverlaySnap.Snapped?>(null) }
        val guides = if (dragging) snap?.takeIf { it.vertical != null || it.horizontal != null } else null
        if (guides != null) lastSnap = guides
        AnimatedVisibility(guides != null, enter = fadeIn(CgMotion.short()), exit = fadeOut(CgMotion.short())) {
            lastSnap?.let { Guides(it) }
        }

        Box(
            Modifier.offset(maxWidth * rect.x, maxHeight * rect.y)
                .size(maxWidth * rect.w, maxHeight * rect.h)
                .onGloballyPositioned { coords[0] = it }
                .drawBehind {
                    if (chrome == 0f) return@drawBehind
                    // A signal outline, thicker while it is held.
                    val stroke = (1f + 1f * grab).dp.toPx()
                    drawRect(
                        CgColors.Brand.Signal.copy(alpha = chrome),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                    // Every corner resizes: a filled signal square centred on it, a size larger while held.
                    val grip = (GRIP + 4f * grab).dp.toPx()
                    for (cx in floatArrayOf(0f, size.width)) for (cy in floatArrayOf(0f, size.height)) {
                        drawRect(
                            CgColors.Brand.Signal.copy(alpha = chrome),
                            topLeft = Offset(cx - grip / 2, cy - grip / 2),
                            size = Size(grip, grip),
                        )
                    }
                }
                .testTag(TestTags.Graph)
                .semantics {
                    role = Role.Button
                    contentDescription = description
                    onClick(styleAction) {
                        tap()
                        true
                    }
                }
                .pointerInput(frameW, frameH) {
                    val touch = CgSize.Touch.toPx()
                    val pullX = SNAP_PULL.dp.toPx() / frameW
                    val pullY = SNAP_PULL.dp.toPx() / frameH
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val at = coords[0] ?: return@awaitEachGesture
                        // The nearest corner within half a touch target, else a move; so a small graph still moves.
                        val corner =
                            NormRect.Corner.entries
                                .map { c ->
                                    val at =
                                        Offset(
                                            if (c.left) 0f else size.width.toFloat(),
                                            if (c.top) 0f else size.height.toFloat(),
                                        )
                                    c to (down.position - at).getDistance()
                                }
                                .filter { it.second <= touch / 2 }
                                .minByOrNull { it.second }
                                ?.first
                        val start = at.localToRoot(down.position)
                        var last = start
                        var moving = false
                        var free = current
                        var push = true
                        var lastTarget: Pair<OverlaySnap.Place?, OverlaySnap.Place?>? = null
                        while (true) {
                            val ev = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!ev.pressed) {
                                if (!moving) tap()
                                break
                            }
                            val now = (coords[0] ?: at).localToRoot(ev.position)
                            if (!moving && (now - start).getDistance() > viewConfiguration.touchSlop) {
                                moving = true
                                dragging = true
                                last = now
                            }
                            if (!moving) continue
                            ev.consume()
                            val dx = (now - last).x / frameW
                            val dy = (now - last).y / frameH
                            last = now
                            val limit = inside
                            val next =
                                when {
                                    corner != null ->
                                        current.resizedFrom(corner, dx, dy, format, limit ?: NormRect.Frame)
                                    else -> {
                                        free = free.copy(x = free.x + dx, y = free.y + dy)
                                        val s = OverlaySnap.snap(free.clamped(format), pullX, pullY, format)
                                        snap = s
                                        val target = s.vertical?.place to s.horizontal?.place
                                        if (target != lastTarget && (target.first != null || target.second != null)) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        lastTarget = target
                                        limit?.let(s.rect::within) ?: s.rect
                                    }
                                }
                            change(next, push)
                            push = false
                        }
                        dragging = false
                        snap = null
                    }
                }
        )

        // The toolbar rises into its slot over the graph when it is picked, and drops out while it is dragged. As wide
        // as its keys, centred on the graph, over it, or under it where the graph is too near the top.
        if (toolbar != null) {
            val above = maxHeight * rect.y - TOOLBAR_GAP - CgSize.Touch
            AnimatedVisibility(
                selected && !dragging,
                Modifier.offset(
                        x = maxWidth * rect.x,
                        y = if (above >= 0.dp) above else maxHeight * rect.bottom + TOOLBAR_GAP,
                    )
                    .width(maxWidth * rect.w)
                    .wrapContentWidth(unbounded = true)
                    .clipToBounds(),
                enter = slideInVertically(CgMotion.offsetSpec) { it },
                exit = slideOutVertically(CgMotion.offsetSpec) { it },
            ) {
                toolbar()
            }
        }
    }
}

/** Dashed orange hairlines across the frame where the graph snapped. */
@Composable
private fun Guides(snap: OverlaySnap.Snapped) {
    Canvas(Modifier.fillMaxSize()) {
        val w = CgStroke.Hairline.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        snap.vertical?.let {
            val y = it.guide * size.height
            drawLine(CgColors.Brand.Signal, Offset(0f, y), Offset(size.width, y), w, pathEffect = dash)
        }
        snap.horizontal?.let {
            val x = it.guide * size.width
            drawLine(CgColors.Brand.Signal, Offset(x, 0f), Offset(x, size.height), w, pathEffect = dash)
        }
    }
}

/**
 * The frame's top and bottom bands that a sharing app's header and captions cover (where the graph snaps below and
 * above), hatched with bone stripes at 45°: 6 dp on, 6 dp off.
 */
@Composable
private fun SafeBands() {
    Canvas(Modifier.fillMaxSize()) {
        val stripe = HATCH.dp.toPx()
        val step = stripe * 2 * sqrt(2f)
        val ink = CgColors.Text.Primary.copy(alpha = 0.10f)
        fun band(top: Float, bottom: Float) =
            clipRect(0f, top, size.width, bottom) {
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(ink, Offset(x, size.height), Offset(x + size.height, 0f), stripe)
                    x += step
                }
            }
        band(0f, OverlaySnap.SAFE_TOP * size.height)
        band((1f - OverlaySnap.SAFE_BOTTOM) * size.height, size.height)
    }
}

/** A hatch stripe's width, and the gap after it, dp. */
private const val HATCH = 6

/** A corner grip's side, dp: the canvas's 11 px square. */
private const val GRIP = 11f

/** Between the graph and its toolbar. */
private val TOOLBAR_GAP = 12.dp

/** How close, in dp, a moving graph has to come to a snap target before it is pulled onto it. */
private const val SNAP_PULL = 8

/** The pill's name for a snap place. */
private val OverlaySnap.Place.label: StringResource
    get() =
        when (this) {
            OverlaySnap.Place.TOP_BELOW_HEADER -> Res.string.snap_top_below_header
            OverlaySnap.Place.TOP -> Res.string.snap_top
            OverlaySnap.Place.MIDDLE -> Res.string.snap_middle
            OverlaySnap.Place.BOTTOM_ABOVE_CAPTIONS -> Res.string.snap_bottom_above_captions
            OverlaySnap.Place.BOTTOM -> Res.string.snap_bottom
            OverlaySnap.Place.LEFT -> Res.string.snap_left
            OverlaySnap.Place.CENTRE -> Res.string.snap_centre
            OverlaySnap.Place.RIGHT -> Res.string.snap_right
        }
