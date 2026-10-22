package app.onlyshots.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The graph made editable on a preview (record and edit), as in the web editor: drag inside to move it, drag a grip to
 * resize it, tap it for its style. While it moves, the areas a sharing app covers are shaded and it snaps to the lines
 * around them, the middle and the side margins, with a guide, a label and a tick. Preview only — nothing here reaches
 * the recording or the export.
 *
 * [selected] shows the outline, the grips and the [toolbar]; on the record screen it is always on. [leftGrip] adds a
 * second grip in the bottom-left corner, for the record screen, where the camera rail covers the right-hand one.
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
    leftGrip: Boolean = false,
    /** The frame's shape: its minimum graph size and where the graph snaps. */
    format: VideoFormat = VideoFormat.PORTRAIT_9_16,
    /**
     * The part of the frame the graph is kept in, where not all of it is on screen (the record screen's camera fills
     * the window, so the frame's edges are cut off); null for the whole frame.
     */
    bounds: NormRect? = null,
    toolbar: (@Composable () -> Unit)? = null,
) {
    val change by rememberUpdatedState(onChange)
    val tap by rememberUpdatedState(onTap)
    val current by rememberUpdatedState(rect)
    val inside by rememberUpdatedState(bounds)
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
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
                    val stroke = (1.5f + 1.5f * grab).dp.toPx()
                    drawRect(
                        Color.White.copy(alpha = 0.92f * chrome),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                    )
                    // The corners that resize wear a thick bracket along the outline: where the finger lands.
                    val bracket = (4f + 2f * grab).dp.toPx()
                    val arm = 18.dp.toPx()
                    val y = size.height - bracket / 2
                    fun corner(x: Float, inward: Float) {
                        val path =
                            Path().apply {
                                moveTo(x, y - arm)
                                lineTo(x, y)
                                lineTo(x + inward * arm, y)
                            }
                        drawPath(
                            path,
                            Color.White.copy(alpha = chrome),
                            style = Stroke(bracket, cap = StrokeCap.Square),
                        )
                    }
                    corner(size.width - bracket / 2, -1f)
                    if (leftGrip) corner(bracket / 2, 1f)
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
                .pointerInput(frameW, frameH, leftGrip) {
                    val touch = CgSize.Touch.toPx()
                    val pullX = SNAP_PULL.dp.toPx() / frameW
                    val pullY = SNAP_PULL.dp.toPx() / frameH
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val at = coords[0] ?: return@awaitEachGesture
                        val bottom = down.position.y > size.height - touch
                        val rightGrip = bottom && down.position.x > size.width - touch
                        val leftHit = leftGrip && bottom && !rightGrip && down.position.x < touch
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
                                    rightGrip ->
                                        current.resizedBy(dx, dy, format).let { r ->
                                            if (limit == null) r
                                            else
                                                r.copy(
                                                    w = r.w.coerceAtMost(limit.right - r.x),
                                                    h = r.h.coerceAtMost(limit.bottom - r.y),
                                                )
                                        }
                                    leftHit ->
                                        current.resizedFromLeftBy(dx, dy, format).let { r ->
                                            if (limit == null || r.x >= limit.x) r
                                            else r.copy(x = limit.x, w = r.right - limit.x)
                                        }
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

        // The snap's name, in an orange pill under the graph.
        val snapName =
            snap
                ?.let { s -> listOfNotNull(s.vertical?.place, s.horizontal?.place).map { stringResource(it.label) } }
                ?.joinToString(" · ")
        var lastName by remember { mutableStateOf("") }
        val showName = dragging && !snapName.isNullOrEmpty()
        if (showName) lastName = snapName.orEmpty()
        AnimatedVisibility(
            showName,
            enter = fadeIn(CgMotion.short()) + scaleIn(CgMotion.springy(), initialScale = 0.85f),
            exit = fadeOut(CgMotion.short()),
        ) {
            Text(
                lastName,
                style = CgTheme.type.labelStrong,
                color = CgColors.Text.OnLight,
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentWidth()
                        .offset(y = (maxHeight * rect.bottom + 8.dp).coerceAtMost(maxHeight - 36.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(CgColors.Brand.Orange)
                        .padding(horizontal = CgSpace.S12, vertical = CgSpace.S6),
            )
        }

        // The toolbar rises into place over the graph when it is picked, and steps aside while it is dragged.
        if (toolbar != null) {
            AnimatedVisibility(
                selected && !dragging,
                Modifier.fillMaxWidth().wrapContentWidth().offset(y = (maxHeight * rect.y - 52.dp).coerceAtLeast(8.dp)),
                enter = fadeIn(CgMotion.short()) + slideInVertically(CgMotion.offsetSpec) { it / 3 },
                exit = fadeOut(CgMotion.short()),
            ) {
                toolbar()
            }
        }
    }
}

/** Orange lines across the frame where the graph snapped. */
@Composable
private fun Guides(snap: OverlaySnap.Snapped) {
    Canvas(Modifier.fillMaxSize()) {
        val w = 3.dp.toPx()
        snap.vertical?.let {
            drawRect(CgColors.Brand.Orange, Offset(0f, it.guide * size.height - w / 2), Size(size.width, w))
        }
        snap.horizontal?.let {
            drawRect(CgColors.Brand.Orange, Offset(it.guide * size.width - w / 2, 0f), Size(w, size.height))
        }
    }
}

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
