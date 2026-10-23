package app.onlyshots.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import app.onlyshots.SharedKeys
import app.onlyshots.TestTags
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_focus_locked
import app.onlyshots.core.resources.record_needs_camera
import app.onlyshots.core.resources.text
import app.onlyshots.edit.EditSheet
import app.onlyshots.edit.GraphToolbar
import app.onlyshots.edit.OverlayEditLayer
import app.onlyshots.machineLabel
import app.onlyshots.overlay.compose.ShotOverlay
import app.onlyshots.shareBounds
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * What the camera pane draws from, read afresh by the one pane every layout shares. A data class: the same inputs again
 * (a recomposition above for something else) do not redraw the camera pane and its graph.
 */
internal data class PaneInputs(
    val state: RecordUiState,
    val camera: CameraState,
    val settings: RecordingSettings,
    val granted: Boolean,
    /** Without the camera: asks for it again, or opens the system settings once asking no longer works. */
    val onAllowCamera: () -> Unit,
    val allowCameraLabel: String,
    val graphStyle: GraphStyle,
    val actions: RecordActions,
    /** How bright the picture is behind the graph, for styles that adapt; read where the graph is drawn. */
    val backdropLuma: () -> Float?,
    val picture: @Composable (Modifier, drawFrames: Boolean) -> Unit,
    val capture: VideoFormat,
)

/** The graph's selection on the picture, held by the body: its toolbar opens the sheets the body places. */
internal class GraphSelection(
    val selected: Boolean,
    val onSelected: (Boolean) -> Unit,
    val onSheet: (EditSheet) -> Unit,
    /** While the graph is moved the floating controls step aside, and it may go under them. */
    val onMoving: (Boolean) -> Unit,
)

/**
 * The picture and the overlay. The picture is what the camera films, its whole capture frame, covering the pane: on a
 * screen longer than the frame its edges are cut off here, though not in the video. The graph is kept where the screen
 * shows it, under the floating controls too ([clear], root px, is only where the viewfinder's marks go); while it is
 * moved, the [graph] selection says so, so the controls step aside. [chrome] is what else the layout leaves to it.
 */
@Composable
internal fun CameraPane(
    inputs: PaneInputs,
    chrome: PaneChrome,
    /**
     * Read only where the viewfinder's marks are drawn: it moves with the controls, and must not recompose the pane.
     */
    clear: () -> Rect?,
    graph: GraphSelection,
    modifier: Modifier = Modifier,
) {
    val state = inputs.state
    val capture = inputs.capture
    // The camera picture is Haze's source, so styles that blur what is behind the graph blur the live picture.
    val haze = rememberHazeState()
    // Where the last tap focused, and a count so every tap restarts the ring.
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(inputs.camera.focusLocked) { if (!inputs.camera.focusLocked) focusAt = null }
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // The frame covering the pane, and what of it is on screen.
        val frame = cameraFrame(maxWidth.value, maxHeight.value, capture.ratio)
        var frameInRoot by remember { mutableStateOf(Rect.Zero) }
        // The graph may go anywhere the frame is on screen, under the floating controls too: they are the screen's,
        // not the video's, and they step aside while it moves.
        val bounds = context(capture) { graphBounds(frame.shown) }
        val rect = inputs.settings.overlay.rect.within(bounds)
        // The view model keeps the placed graph in what the screen shows.
        LaunchedEffect(bounds, inputs.actions) { inputs.actions.keepGraphWithin(bounds) }
        Box(
            Modifier.requiredSize(frame.width.dp, frame.height.dp)
                // The frame the editor opens on once this take is saved: it carries over from here.
                .shareBounds(SharedKeys.TAKE_FRAME, overlay = false)
                // Its whole size, not the part on screen that its bounds in the root would clip it to.
                .onGloballyPositioned { frameInRoot = Rect(it.positionInRoot(), it.size.toSize()) }
                .background(CgColors.Surface.Scrim)
        ) {
            if (inputs.granted) {
                LivePicture(inputs, haze, graph, focusAt, focusTaps) {
                    focusAt = it
                    focusTaps++
                }
            } else {
                // No picture: the viewfinder's marks on the part of the window the controls leave, and why.
                Viewfinder({ clear() ?: shownIn(frameInRoot, frame.shown) }, frameInRoot)
                NoCameraPrompt(
                    inputs.allowCameraLabel,
                    inputs.onAllowCamera,
                    Modifier.matchParentSize().padding(frame.shownPadding()),
                )
            }
            AnimatedVisibility(
                state.showsGraph(inputs.granted, chrome),
                Modifier.fillMaxSize(),
                enter = fadeIn(CgMotion.long()),
                exit = fadeOut(CgMotion.medium()),
            ) {
                LiveGraph(inputs, rect, haze)
            }
            AnimatedVisibility(
                state.editsGraph(inputs.granted, chrome),
                Modifier.fillMaxSize(),
                enter = fadeIn(CgMotion.medium()),
                exit = fadeOut(CgMotion.short()),
            ) {
                GraphPlacing(inputs, rect, bounds, graph)
            }
            // Kept once the ring goes out, so the label says where it was while it goes too.
            var ring by remember { mutableStateOf<Offset?>(null) }
            if (focusAt != null) ring = focusAt
            if (state.showsFocusLock(inputs.camera, focusAt != null, inputs.granted, chrome)) {
                ring?.let { FocusLockLabel(it, inputs.actions::unlockFocus, Modifier.matchParentSize()) }
            }
        }
        if (chrome == PaneChrome.Own) PaneControls(inputs, rect)
    }
}

/**
 * The camera's picture, and over it the gestures: a tap focuses there ([onFocus]), or puts a selected graph down; a
 * pinch zooms. The ring marks the spot last focused, [focusAt], restarting with every tap ([focusTaps]).
 */
@Composable
private fun LivePicture(
    inputs: PaneInputs,
    haze: HazeState,
    graph: GraphSelection,
    focusAt: Offset?,
    focusTaps: Int,
    onFocus: (Offset) -> Unit,
) {
    val actions = inputs.actions
    val zoom by rememberUpdatedState(inputs.camera.zoom)
    val selected by rememberUpdatedState(graph.selected)
    val recording by rememberUpdatedState(inputs.state.recording)
    val focused by rememberUpdatedState(onFocus)
    inputs.picture(Modifier.fillMaxSize().clipToBounds().hazeSource(haze), inputs.graphStyle.blursVideo)
    // The gestures sit on a layer above the preview, not on it: a platform view (PreviewView,
    // AVCaptureVideoPreviewLayer's view) takes the touches for itself before Compose sees them.
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    // With the graph selected, a tap on the picture puts it down rather than refocusing.
                    if (selected) {
                        graph.onSelected(false)
                    } else {
                        // Focus and exposure on that spot, held there until "auto" is tapped.
                        actions.focusAt(tap.x / size.width, tap.y / size.height)
                        focused(tap)
                    }
                }
            }
            .pointerInput(Unit) {
                // Pinch to zoom, as in the camera app; not mid-shot, where the lenses are hidden too.
                detectTransformGestures { _, _, pinch, _ ->
                    if (!recording && pinch != 1f) actions.setZoom(zoom * pinch)
                }
            }
    )
    focusAt?.let { FocusRing(it, focusTaps) }
}

/**
 * Under the ring, as the camera apps put their lock label, in words alone: tap it to go back to continuous focus and
 * exposure. A held focus survives into the recording, as it should. It lights with the ring and goes out with it, like
 * a lamp: no motion of its own.
 */
@Composable
private fun FocusLockLabel(ring: Offset, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        // Just the words, no frame: the ring above already marks the spot.
        Text(
            stringResource(Res.string.record_focus_locked).caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Status.Warning,
            modifier =
                Modifier.belowFocusRing(ring, grow = FOCUS_LABEL_GROW)
                    .clickable(role = Role.Button, onClick = onUnlock)
                    .padding(horizontal = CgSpace.S8, vertical = CgSpace.S12 + FOCUS_LABEL_GROW),
        )
    }
}

/** Without the camera: why, and the button that asks for it again (or opens the settings). */
@Composable
private fun NoCameraPrompt(label: String, onAllowCamera: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Column(
            Modifier.align(Alignment.CenterStart).padding(horizontal = CgSpace.S16 * 2),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
        ) {
            Text(
                stringResource(Res.string.record_needs_camera),
                style = CgTheme.type.screenTitle.copy(letterSpacing = 0.sp, lineHeight = 26.sp),
            )
            CgButton(
                label,
                onClick = onAllowCamera,
                kind = CgButtonKind.Signal,
                modifier = Modifier.testTag(TestTags.RecordAllowCamera),
            )
        }
    }
}

/** The live graph at [rect]: the shot so far, in the graph's style, over the picture. */
@Composable
private fun LiveGraph(inputs: PaneInputs, rect: NormRect, haze: HazeState) {
    val state = inputs.state
    val overlay = inputs.settings.overlay
    ShotOverlay(
        shot = state.shot,
        videoTimeUs = state.videoTimeUs,
        shotStartUs = state.shotStartInVideoUs,
        // Remembered: a new copy on every pass would make the graph rebuild for nothing.
        settings = remember(overlay, state.sourceKind, rect) { liveOverlay(overlay, state.sourceKind, rect) },
        // From the state, not the view model: the machine can change underneath.
        style = inputs.graphStyle,
        title =
            graphTitle(
                state.shot.profileName,
                RecordSession.defaultProfileName(state.sourceKind).text(),
                machineLabel(state.sourceKind),
            ),
        fonts = CgTheme.overlayFonts,
        modifier = Modifier.fillMaxSize(),
        hazeState = haze,
        backdropLuma = inputs.backdropLuma(),
    )
}

/** The graph's handles and toolbar: placed by hand before a shot. */
@Composable
private fun GraphPlacing(inputs: PaneInputs, rect: NormRect, bounds: NormRect, graph: GraphSelection) {
    val actions = inputs.actions
    OverlayEditLayer(
        rect = rect,
        onChange = { moved, _ -> actions.updateOverlay { it.copy(rect = moved) } },
        onTap = { graph.onSelected(!graph.selected) },
        selected = graph.selected,
        format = inputs.capture,
        bounds = bounds,
        onDragging = graph.onMoving,
        toolbar = {
            GraphToolbar(
                opacity = inputs.settings.overlay.opacity,
                onStyle = { graph.onSheet(EditSheet.STYLE) },
                onData = { graph.onSheet(EditSheet.DATA) },
                onOpacity = { o -> actions.updateOverlay { it.withOpacity(o) } },
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** A pane of its own keeps its status and lenses on the picture, where no floating controls have them. */
@Composable
private fun BoxScope.PaneControls(inputs: PaneInputs, rect: NormRect) {
    val state = inputs.state
    // While filming, the panel under the picture says how long: one clock. The opposite corner from the overlay,
    // which it must never cover: when the graph is dragged across the middle, the chip travels to the other corner
    // rather than jumping there.
    val corner = animateFloatAsState(statusChipBias(rect), CgMotion.settle(), label = "chip")
    AnimatedVisibility(
        state.statusWorthShowing && !state.recording,
        Modifier.matchParentSize().alignedAtStart { corner.value }.padding(CgSpace.S12),
        enter = CgMotion.reveal,
        exit = CgMotion.conceal,
    ) {
        TakeStatus(state)
    }
    // The lenses slide off the edge when recording starts, and back on after.
    AnimatedVisibility(
        state.showsLenses(inputs.granted),
        Modifier.align(Alignment.CenterEnd).padding(CgSpace.S12),
        enter = slideInHorizontally(CgMotion.offsetSpec) { it * 2 },
        exit = slideOutHorizontally(CgMotion.offsetSpec) { it * 2 },
    ) {
        LensPicker(inputs.camera, inputs.actions::setZoom, vertical = true)
    }
}

/**
 * A viewfinder with no picture in it: four corner marks round what [area] gives (root px) and a cross in its middle, in
 * hairline grey, drawn in the [frame]'s own coordinates.
 */
@Composable
private fun BoxScope.Viewfinder(area: () -> Rect, frame: Rect) {
    Box(
        Modifier.matchParentSize().drawWithCache {
            // Built when the size or the area changes, not on every draw; the area is read here, not in composition.
            val area = area()
            val inset = CgSpace.S16.toPx()
            val arm = CgSpace.S16.toPx()
            val l = area.left - frame.left + inset
            val t = area.top - frame.top + inset
            val r = area.right - frame.left - inset
            val b = area.bottom - frame.top - inset
            val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Square)
            fun mark(x: Float, y: Float, dx: Float, dy: Float) =
                Path().apply {
                    moveTo(x, y + dy * arm)
                    lineTo(x, y)
                    lineTo(x + dx * arm, y)
                }
            val marks = listOf(mark(l, t, 1f, 1f), mark(r, t, -1f, 1f), mark(l, b, 1f, -1f), mark(r, b, -1f, -1f))
            val c = Offset((l + r) / 2f, (t + b) / 2f)
            val half = arm / 2f
            onDrawBehind {
                if (area.isEmpty || frame.isEmpty) return@onDrawBehind
                for (m in marks) drawPath(m, CgColors.Line.Hairline, style = stroke)
                drawLine(CgColors.Line.Hairline, c - Offset(half, 0f), c + Offset(half, 0f), stroke.width)
                drawLine(CgColors.Line.Hairline, c - Offset(0f, half), c + Offset(0f, half), stroke.width)
            }
        }
    )
}

/**
 * Where a child of the whole box sits, BiasAlignment's way: at the start, and [vertical] (-1 top, 1 bottom) of the way
 * down. Read while placing, so an animated bias moves the child without recomposing it.
 */
private fun Modifier.alignedAtStart(vertical: () -> Float): Modifier = layout { measurable, constraints ->
    val child = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val center = (constraints.maxHeight - child.height).toFloat() / 2f
        child.placeRelative(0, (center * (1f + vertical())).roundToInt())
    }
}

/**
 * Places a label centred under the focus ring at [ring], or over it when there is no room below, kept inside the
 * picture.
 */
private fun Modifier.belowFocusRing(ring: Offset, grow: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val label = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    // A label padded [grow] more on each side sits [grow] nearer the ring, so its words stay where they were.
    val gap = (FOCUS_RING_RADIUS.dp + FOCUS_LABEL_GAP).roundToPx() - grow.roundToPx()
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = (ring.x.toInt() - label.width / 2).coerceIn(0, maxOf(0, constraints.maxWidth - label.width))
        val below = ring.y.toInt() + gap
        val y = if (below + label.height <= constraints.maxHeight) below else ring.y.toInt() - gap - label.height
        label.place(x, y.coerceAtLeast(0))
    }
}

/** What the focus label's padding grows by, to a 44 dp target, with its words where they were. */
private val FOCUS_LABEL_GROW = 2.dp

/** The space between the focus ring and its label. */
private val FOCUS_LABEL_GAP = 4.dp
