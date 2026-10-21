package app.onlyshots.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.SharedKeys
import app.onlyshots.StyleEditorRoute
import app.onlyshots.TestTags
import app.onlyshots.camera.CameraError
import app.onlyshots.camera.CameraPreview
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.Banner
import app.onlyshots.core.designsystem.component.BannerKind
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.EditorSheet
import app.onlyshots.core.designsystem.component.EditorSheetSlot
import app.onlyshots.core.designsystem.component.RecordButton
import app.onlyshots.core.designsystem.component.RecordButtonState
import app.onlyshots.core.designsystem.component.TwoPane
import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Series
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.camera_no_rear
import app.onlyshots.core.resources.camera_no_space
import app.onlyshots.core.resources.camera_recording_failed
import app.onlyshots.core.resources.record_allow_camera
import app.onlyshots.core.resources.record_data
import app.onlyshots.core.resources.record_focus_locked
import app.onlyshots.core.resources.record_needs_camera
import app.onlyshots.core.resources.record_open_settings
import app.onlyshots.core.resources.record_ready
import app.onlyshots.core.resources.record_settings_title
import app.onlyshots.core.resources.record_style
import app.onlyshots.core.resources.text
import app.onlyshots.edit.DataSheetContent
import app.onlyshots.edit.EditSheet
import app.onlyshots.edit.GraphToolbar
import app.onlyshots.edit.OverlayEditLayer
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.machineLabel
import app.onlyshots.overlay.compose.ShotOverlay
import app.onlyshots.platform.KeepScreenOn
import app.onlyshots.platform.LockOrientation
import app.onlyshots.platform.rememberCameraGranted
import app.onlyshots.platform.rememberMediaPermission
import app.onlyshots.platform.rememberOpenAppSettings
import app.onlyshots.shareBounds
import app.onlyshots.style.StylePicker
import app.onlyshots.style.StylePickerActions
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit,
    onStyleEditor: (StyleEditorRoute) -> Unit = {},
    savedStyle: StateFlow<String?> = MutableStateFlow(null),
    onSavedStyleUsed: () -> Unit = {},
    playSample: Boolean = false,
    viewModel: RecordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera by viewModel.cameraState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val customStyles by viewModel.styles.customStyles.collectAsStateWithLifecycle(viewModel.styles.custom)
    // A style just saved in the style editor becomes the graph's style.
    val justSaved by savedStyle.collectAsStateWithLifecycle()
    LaunchedEffect(justSaved) {
        justSaved?.let {
            viewModel.pickStyle(it)
            onSavedStyleUsed()
        }
    }
    var cameraGranted by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(false) }

    // The microphone is always on when allowed; the editor mutes a take that should be silent.
    // Once a request from "allow camera" comes back refused, the system will not ask again: the button sends the user
    // to the app's settings instead.
    var refusedFromButton by remember { mutableStateOf(false) }
    var askedFromButton by remember { mutableStateOf(false) }
    // Whether the first answer is in: until then the screen's own first request is under way, and asking again would
    // arm the camera twice.
    var answered by remember { mutableStateOf(false) }
    val askMedia = rememberMediaPermission { camera, mic ->
        answered = true
        cameraGranted = camera
        micGranted = mic
        viewModel.cameraAccess(camera)
        if (askedFromButton && !camera) refusedFromButton = true
        askedFromButton = false
        if (camera) viewModel.arm(mic)
    }
    val cameraAllowedNow = rememberCameraGranted()
    val openSettings = rememberOpenAppSettings()

    LaunchedEffect(Unit) { askMedia() }
    // Opened from Home's "try a sample shot": the replayed shot starts by itself once the camera is armed.
    LaunchedEffect(playSample) { if (playSample) viewModel.simulateShotWhenArmed() }
    // A finished recording goes straight to the editor: trim, place the graph, export.
    LaunchedEffect(viewModel) { viewModel.saved.collect(onSaved) }
    // Leaving the app saves the recording; coming back re-arms.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.backgrounded() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        when {
            cameraGranted -> viewModel.arm(micGranted)
            // Back from the system settings with the camera allowed: read both answers again (no prompt for what
            // is already decided) and arm.
            answered && cameraAllowedNow() -> askMedia()
        }
    }
    KeepScreenOn(state.phase == RecordPhase.ARMED || state.recording)
    // The camera films its whole frame the way the screen stands: 9:16 upright, 16:9 on its side. Turned while armed,
    // the pre-roll starts again the new way; while a take runs the screen holds still, and asks again once it ends.
    // Measured here rather than read from the window's info, which a rotation handled in place can leave stale.
    var size by remember { mutableStateOf(IntSize.Zero) }
    val standing = formatThatFits(size.width, size.height)
    LaunchedEffect(standing, state.recording) { if (size != IntSize.Zero) viewModel.capture(standing, micGranted) }
    val capture by viewModel.captureFormat.collectAsStateWithLifecycle()
    if (state.recording) LockOrientation(landscape = capture.landscape)
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    // Kept as state and read only by the tiles that show it: a new sample redraws them, not the camera and its graph.
    val live = viewModel.live.collectAsStateWithLifecycle()
    RecordBody(
        modifier = Modifier.onSizeChanged { size = it },
        state = state,
        camera = camera,
        settings = settings,
        graphStyle = viewModel.style(settings.overlay, state.sourceKind),
        customStyles = customStyles,
        granted = cameraGranted,
        canSimulateShot = viewModel.canSimulateShot,
        onAllowCamera = {
            if (refusedFromButton) openSettings()
            else {
                askedFromButton = true
                askMedia()
            }
        },
        allowCameraLabel =
            stringResource(if (refusedFromButton) Res.string.record_open_settings else Res.string.record_allow_camera),
        saveError = saveError,
        actions = viewModel,
        onBack = onBack,
        onStyleEditor = onStyleEditor,
        sampleLuma = viewModel.camera::sampleLuma,
        capture = capture,
        live = { live.value },
        picture = { modifier, drawFrames -> CameraPreview(viewModel.camera, modifier, drawFrames = drawFrames) },
    )
}

/**
 * The record screen drawn from plain state: the session's [state], the [camera]'s, the recording [settings] and the
 * graph's resolved [graphStyle]; [actions] to ask for changes. [picture] draws the camera into the modifier it is given
 * (the platform preview in the app, a still in a screenshot), [sampleLuma] measures it for styles that adapt.
 */
@Composable
fun RecordBody(
    state: RecordUiState,
    camera: CameraState,
    settings: RecordingSettings,
    graphStyle: GraphStyle,
    customStyles: List<GraphStyle>,
    granted: Boolean,
    canSimulateShot: Boolean,
    saveError: UiText?,
    actions: RecordActions,
    /** Without the camera: asks for it again, or opens the system settings once asking no longer works. */
    onAllowCamera: () -> Unit = {},
    allowCameraLabel: String = stringResource(Res.string.record_allow_camera),
    onBack: () -> Unit,
    onStyleEditor: (StyleEditorRoute) -> Unit,
    sampleLuma: (x: Float, y: Float, w: Float, h: Float) -> Float?,
    picture: @Composable (Modifier, drawFrames: Boolean) -> Unit,
    /** The camera's whole frame as it films it: 9:16 with the screen upright, 16:9 on its side. */
    capture: VideoFormat = VideoFormat.PORTRAIT_9_16,
    /** The source's newest reading, for the tiles before a take starts; read where the tiles are, not here. */
    live: () -> Sample? = { null },
    modifier: Modifier = Modifier,
    /** The graph selected from the start, its toolbar showing: for a screenshot. */
    graphSelectedAtFirst: Boolean = false,
) {
    val layout = LocalWindowLayout.current
    var settingsOpen by remember { mutableStateOf(false) }
    // The graph is edited as on the edit screen: tap it to select it, then its toolbar opens style and data.
    var graphSelected by remember { mutableStateOf(graphSelectedAtFirst) }
    var graphSheet by remember { mutableStateOf<EditSheet?>(null) }
    LaunchedEffect(state.recording, state.hasData) {
        if (state.recording || !state.hasData) {
            graphSelected = false
            graphSheet = null
            settingsOpen = false
        }
    }
    val openRecordingSettings = {
        graphSheet = null
        graphSelected = false
        settingsOpen = true
    }
    // What of the window the floating controls leave free, in root px: the graph is kept there.
    var clear by remember { mutableStateOf<Rect?>(null) }
    // While the graph is moved the floating controls step aside, and it may go under them.
    var graphMoving by remember { mutableStateOf(false) }
    val take =
        TakeInputs(
            state = state,
            camera = camera,
            settings = settings,
            live = live,
            actions = actions,
            canSimulateShot = canSimulateShot,
            saveError = saveError,
            onBack = onBack,
            onSettings = openRecordingSettings,
        )
    // A pane of its own (a tabletop's upper half, a cover screen) keeps its rail and status on the picture; full
    // screen,
    // the floating controls have them.
    // One camera node for every layout: moved between branches (a foldable changing posture) it keeps the preview
    // surface and its binding, where creating it anew would rebind the camera and end the file being recorded. It is
    // created once, so it reads what changes through this rather than keeping what it first saw.
    val inputs =
        rememberUpdatedState(
            PaneInputs(
                state,
                camera,
                settings,
                granted,
                onAllowCamera,
                allowCameraLabel,
                graphStyle,
                actions,
                sampleLuma,
                picture,
                capture,
            )
        )
    val pane = remember {
        movableContentOf { modifier: Modifier, ownControls: Boolean ->
            val now = inputs.value
            CameraPane(
                state = now.state,
                camera = now.camera,
                settings = now.settings,
                granted = now.granted,
                onAllowCamera = now.onAllowCamera,
                allowCameraLabel = now.allowCameraLabel,
                graphStyle = now.graphStyle,
                actions = now.actions,
                sampleLuma = now.sampleLuma,
                picture = now.picture,
                capture = now.capture,
                clear = clear.takeUnless { ownControls },
                graphSelected = graphSelected,
                onGraphSelected = { graphSelected = it },
                onGraphSheet = { graphSheet = it },
                onGraphMoving = { graphMoving = it },
                ownControls = ownControls,
                modifier = modifier,
            )
        }
    }

    Box(modifier.fillMaxSize().background(CgColors.Surface.Base)) {
        when (layout.kind) {
            // A flip's cover screen: the picture, the clock over it and the button under it, nothing else.
            LayoutKind.Tiny ->
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    pane(Modifier.fillMaxSize(), true)
                    TinyControls(state, actions)
                }

            // Resting on its side: the camera in the upper half, the controls on the half lying flat.
            LayoutKind.Stacked ->
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    TwoPane(
                        vertical = true,
                        splitDp = layout.splitDp.dp,
                        hingeDp = layout.hingeDp.dp,
                        first = { pane(Modifier.fillMaxSize(), true) },
                        second = {
                            Box(Modifier.fillMaxSize().background(CgColors.Surface.Bar)) {
                                if (layout.onItsSide)
                                    TabletopPanel(
                                        take,
                                        zoom = { m ->
                                            if (!state.recording && granted) LensPicker(camera, actions::setZoom, m)
                                        },
                                    )
                                else FlexPanel(take)
                            }
                        },
                    )
                }

            // A phone, a tablet, an open foldable: the camera fills the window, under the system bars too, and the
            // controls float over it. Nothing around the picture changes its size, which would rebind the camera.
            LayoutKind.Phone,
            LayoutKind.Wide -> {
                pane(Modifier.fillMaxSize(), false)
                val controls by animateFloatAsState(if (graphMoving) 0f else 1f, CgMotion.short(), label = "controls")
                FloatingControls(
                    take,
                    modifier = Modifier.graphicsLayer { alpha = controls },
                    landscape = capture.landscape,
                    graphOnTop = settings.overlay.rect.let { it.y + it.h / 2f < 0.5f },
                    zoom = { modifier, vertical ->
                        if (!state.recording && granted) LensPicker(camera, actions::setZoom, modifier, vertical)
                    },
                    onClear = { clear = it },
                )
            }
        }
    }

    // Where the sheets go: on a fold the right page, all of it; on a tablet 640 dp wide at most, as every sheet; on a
    // phone across the bottom.
    val onFold = layout.fold && layout.kind == LayoutKind.Wide
    val sheetArea =
        if (onFold) Modifier.fillMaxSize().padding(start = (layout.splitDp + layout.hingeDp).dp)
        else Modifier.fillMaxSize()
    val sheetSize =
        if (onFold) Modifier.fillMaxSize()
        else Modifier.widthIn(max = SHEET_MAX_WIDTH).fillMaxHeight(0.72f).wrapContentHeight(Alignment.Bottom)
    // The graph for this and the next recordings; a recorded video keeps its own copy, edited in the editor.
    Box(sheetArea, contentAlignment = Alignment.BottomCenter) {
        EditorSheetSlot(graphSheet, sheetSize, scrim = true, onScrimTap = { graphSheet = null }) { sheet ->
            EditorSheet(
                title =
                    stringResource(if (sheet == EditSheet.STYLE) Res.string.record_style else Res.string.record_data),
                onDone = { graphSheet = null },
            ) {
                if (sheet == EditSheet.STYLE) {
                    StylePicker(
                        currentId = graphStyle.id,
                        custom = customStyles,
                        actions =
                            StylePickerActions(
                                pick = actions::pickStyle,
                                create = { base -> onStyleEditor(StyleEditorRoute(baseId = base, camera = true)) },
                                edit = { id -> onStyleEditor(StyleEditorRoute(editId = id, camera = true)) },
                                rename = actions::renameStyle,
                                delete = actions::deleteStyle,
                            ),
                    )
                } else {
                    // No finished shot yet, so what can be drawn is what the machine sends.
                    DataSheetContent(settings.overlay, Capabilities.of(state.sourceKind, hasScale = true).series) {
                        change ->
                        actions.updateOverlay(change)
                    }
                }
            }
        }
    }

    if (onFold) {
        Box(sheetArea, contentAlignment = Alignment.BottomCenter) {
            EditorSheetSlot(Unit.takeIf { settingsOpen }, Modifier.fillMaxSize()) {
                EditorSheet(
                    title = stringResource(Res.string.record_settings_title),
                    onDone = { settingsOpen = false },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RecordingSettingsContent(settings, state.sourceKind, actions::updateSettings)
                }
            }
        }
    } else if (settingsOpen) {
        RecordingSheet(
            settings = settings,
            sourceKind = state.sourceKind,
            onChange = actions::updateSettings,
            onDismiss = { settingsOpen = false },
        )
    }
}

/**
 * The picture and the overlay. The picture is what the camera films, its whole [capture] frame, covering the pane: on a
 * screen longer than the frame its edges are cut off here, though not in the video. The graph is kept where the screen
 * shows it, under the floating controls too ([clear], root px, is only where the viewfinder's marks go); while it is
 * moved, [onGraphMoving] says so, so the controls step aside.
 */
@Composable
private fun CameraPane(
    state: RecordUiState,
    camera: CameraState,
    settings: RecordingSettings,
    granted: Boolean,
    onAllowCamera: () -> Unit,
    allowCameraLabel: String,
    graphStyle: GraphStyle,
    actions: RecordActions,
    sampleLuma: (x: Float, y: Float, w: Float, h: Float) -> Float?,
    picture: @Composable (Modifier, drawFrames: Boolean) -> Unit,
    capture: VideoFormat,
    clear: Rect?,
    graphSelected: Boolean,
    onGraphSelected: (Boolean) -> Unit,
    onGraphSheet: (EditSheet) -> Unit,
    onGraphMoving: (Boolean) -> Unit,
    /** The lenses and the status chip drawn here, where no floating controls have them. */
    ownControls: Boolean,
    modifier: Modifier = Modifier,
) {
    // On a cover screen or a sliver of split screen there is room for the picture and nothing
    // else: the chip wraps to six lines and the graph card is smaller than its own labels.
    val tiny = LocalWindowLayout.current.kind == LayoutKind.Tiny
    // The camera picture is Haze's source, so styles that blur what is behind the graph blur the live picture.
    val haze = rememberHazeState()
    // Where the last tap focused, and a count so every tap restarts the ring.
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusTaps by remember { mutableStateOf(0) }
    val zoom by rememberUpdatedState(camera.zoom)
    val selected by rememberUpdatedState(graphSelected)
    val recording by rememberUpdatedState(state.recording)
    LaunchedEffect(camera.focusLocked) { if (!camera.focusLocked) focusAt = null }
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // The frame covering the pane, and what of it is on screen.
        val ratio = capture.ratio
        val frameW = if (maxWidth / maxHeight > ratio) maxWidth else maxHeight * ratio
        val frameH = frameW / ratio
        val shownW = (maxWidth / frameW).coerceAtMost(1f)
        val shownH = (maxHeight / frameH).coerceAtMost(1f)
        val shown = NormRect((1f - shownW) / 2f, (1f - shownH) / 2f, shownW, shownH)
        var frame by remember { mutableStateOf(Rect.Zero) }
        // The graph may go anywhere the frame is on screen, under the floating controls too: they are the screen's,
        // not the video's, and they step aside while it moves.
        val bounds = graphBounds(shown, capture)
        val rect = settings.overlay.rect.within(bounds)
        // A graph the screen would cut off is moved where it shows, and kept there: the video draws it as placed.
        LaunchedEffect(bounds, settings.overlay.rect, state.recording, state.hasData) {
            if (!state.recording && state.hasData && !settings.overlay.rect.inside(bounds, BOUNDS_SLACK))
                actions.updateOverlay { it.copy(rect = it.rect.within(bounds)) }
        }
        // A style that adapts to the video follows how bright the picture is behind the graph, sampled twice a second
        // and smoothed so it does not flicker.
        var luma by remember { mutableStateOf<Float?>(null) }
        // Keyed on whether to sample, not on where: a drag moves the rect every frame, and restarting the loop then
        // would sample at frame rate. The loop reads the rect it is at.
        val sampled by rememberUpdatedState(rect)
        LaunchedEffect(graphStyle.adapt.enabled, granted) {
            if (!graphStyle.adapt.enabled || !granted) {
                luma = null
                return@LaunchedEffect
            }
            while (true) {
                val at = sampled
                sampleLuma(at.x, at.y, at.w, at.h)?.let { now -> luma = luma?.let { it + (now - it) * 0.5f } ?: now }
                delay(500)
            }
        }
        Box(
            Modifier.requiredSize(frameW, frameH)
                // The frame the editor opens on once this take is saved: it carries over from here.
                .shareBounds(SharedKeys.TAKE_FRAME, overlay = false)
                // Its whole size, not the part on screen that its bounds in the root would clip it to.
                .onGloballyPositioned { frame = Rect(it.positionInRoot(), it.size.toSize()) }
                .background(CgColors.Surface.Scrim)
        ) {
            if (granted) {
                picture(Modifier.fillMaxSize().clipToBounds().hazeSource(haze), graphStyle.blursVideo)
                // The gestures sit on a layer above the preview, not on it: a platform view (PreviewView,
                // AVCaptureVideoPreviewLayer's view) takes the touches for itself before Compose sees them.
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                // With the graph selected, a tap on the picture puts it down rather than refocusing.
                                if (selected) {
                                    onGraphSelected(false)
                                } else {
                                    // Focus and exposure on that spot, held there until "auto" is tapped.
                                    actions.focusAt(tap.x / size.width, tap.y / size.height)
                                    focusAt = tap
                                    focusTaps++
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
            } else {
                // No picture: the viewfinder's marks on the part of the window the controls leave, and why.
                Viewfinder(clear ?: shownIn(frame, shown), frame)
                Box(Modifier.matchParentSize().padding(shownPadding(frameW, frameH, shown))) {
                    Column(
                        Modifier.align(Alignment.CenterStart).padding(horizontal = CgSpace.S16 * 2),
                        verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
                    ) {
                        Text(
                            stringResource(Res.string.record_needs_camera),
                            style = CgTheme.type.screenTitle.copy(letterSpacing = 0.sp, lineHeight = 26.sp),
                        )
                        CgButton(
                            allowCameraLabel,
                            onClick = onAllowCamera,
                            kind = CgButtonKind.Signal,
                            modifier = Modifier.testTag(TestTags.RecordAllowCamera),
                        )
                    }
                }
            }
            // Without a machine there is nothing to draw: the screen is a camera, and the shot comes in the editor.
            // Without a camera neither: no picture to draw it over.
            AnimatedVisibility(
                !tiny && state.hasData && granted,
                Modifier.fillMaxSize(),
                enter = fadeIn(CgMotion.long()),
                exit = fadeOut(CgMotion.medium()),
            ) {
                ShotOverlay(
                    shot = state.shot,
                    videoTimeUs = state.videoTimeUs,
                    shotStartUs = state.shotStartInVideoUs,
                    // Before the first sample the graph shows every selected reading, waiting: only those this source
                    // can send (a scale has no pressure), as the data sheet offers them.
                    // Remembered: a new copy on every pass would make the graph rebuild for nothing.
                    settings =
                        remember(settings.overlay, state.sourceKind, rect) {
                            settings.overlay.forSource(state.sourceKind).let { o ->
                                val measured = Capabilities.of(state.sourceKind, hasScale = true).series
                                o.copy(
                                    rect = rect,
                                    series =
                                        o.series.filterTo(mutableSetOf()) { it == Series.TARGETS || it in measured },
                                )
                            }
                        },
                    // From the state, not the view model: the machine can change underneath.
                    style = graphStyle,
                    title =
                        "${state.shot.profileName.ifEmpty { RecordSession.defaultProfileName(state.sourceKind).text() }} · " +
                            machineLabel(state.sourceKind),
                    fonts = CgTheme.overlayFonts,
                    modifier = Modifier.fillMaxSize(),
                    hazeState = haze,
                    backdropLuma = luma,
                )
            }
            // Placed by hand before a shot; while recording nothing on the picture moves.
            AnimatedVisibility(
                !tiny && !state.recording && state.hasData && granted,
                Modifier.fillMaxSize(),
                enter = fadeIn(CgMotion.medium()),
                exit = fadeOut(CgMotion.short()),
            ) {
                OverlayEditLayer(
                    rect = rect,
                    onChange = { moved, _ -> actions.updateOverlay { it.copy(rect = moved) } },
                    onTap = { onGraphSelected(!graphSelected) },
                    selected = graphSelected,
                    format = capture,
                    bounds = bounds,
                    onDragging = onGraphMoving,
                    toolbar = {
                        GraphToolbar(
                            opacity = settings.overlay.opacity,
                            onStyle = { onGraphSheet(EditSheet.STYLE) },
                            onData = { onGraphSheet(EditSheet.DATA) },
                            onOpacity = { o -> actions.updateOverlay { it.withOpacity(o) } },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            var ring by remember { mutableStateOf<Offset?>(null) }
            if (focusAt != null) ring = focusAt
            // Under the ring, as the camera apps put their lock label, in words alone: tap it to go back to continuous
            // focus and
            // exposure. A held focus survives into the recording, as it should. It lights with the ring and goes out
            // with it, like a lamp: no motion of its own.
            if (camera.focusLocked && focusAt != null && granted && !tiny && !state.recording) {
                Box(Modifier.matchParentSize()) {
                    ring?.let {
                        // Just the words, no frame: the ring above already marks the spot.
                        Text(
                            stringResource(Res.string.record_focus_locked).caps(),
                            style = CgTheme.type.labelStrong,
                            color = CgColors.Status.Warning,
                            modifier =
                                Modifier.belowFocusRing(it, grow = FOCUS_LABEL_GROW)
                                    .clickable(role = Role.Button, onClick = actions::unlockFocus)
                                    .padding(horizontal = CgSpace.S8, vertical = CgSpace.S12 + FOCUS_LABEL_GROW),
                        )
                    }
                }
            }
        }
        if (ownControls && !tiny) {
            // While filming, the panel under the picture says how long: one clock. The opposite corner from the
            // overlay, which it must never cover: when the graph is dragged across the middle, the chip travels to
            // the other corner rather than jumping there.
            val corner = animateFloatAsState(if (rect.y < 0.5f) 1f else -1f, CgMotion.settle(), label = "chip")
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
                !state.recording && granted,
                Modifier.align(Alignment.CenterEnd).padding(CgSpace.S12),
                enter = slideInHorizontally(CgMotion.offsetSpec) { it * 2 },
                exit = slideOutHorizontally(CgMotion.offsetSpec) { it * 2 },
            ) {
                LensPicker(camera, actions::setZoom, vertical = true)
            }
        }
    }
}

/** Where the graph may go, in the frame's fractions: what of the frame is [shown], a side margin in. */
private fun graphBounds(shown: NormRect, capture: VideoFormat): NormRect {
    var left = shown.x
    var top = shown.y
    var right = shown.right
    var bottom = shown.bottom
    val mx = NormRect.MARGIN_REF / capture.refW
    val my = NormRect.MARGIN_REF / capture.refH
    left += mx
    right -= mx
    top += my
    bottom -= my
    // Never less room than the smallest graph: a sliver of window keeps the whole frame instead.
    if (right - left < NormRect.minW(capture) || bottom - top < NormRect.minH(capture)) return NormRect(0f, 0f, 1f, 1f)
    return NormRect(left, top, right - left, bottom - top)
}

/** What of the [frame] (root px) is on screen: its [shown] fractions. */
private fun shownIn(frame: Rect, shown: NormRect): Rect =
    Rect(
        frame.left + frame.width * shown.x,
        frame.top + frame.height * shown.y,
        frame.left + frame.width * shown.right,
        frame.top + frame.height * shown.bottom,
    )

/** The padding that brings a frame of [frameW] × [frameH] in to the part of it on screen. */
private fun shownPadding(frameW: Dp, frameH: Dp, shown: NormRect): PaddingValues =
    PaddingValues(
        start = frameW * shown.x,
        top = frameH * shown.y,
        end = frameW * (1f - shown.right),
        bottom = frameH * (1f - shown.bottom),
    )

/**
 * A viewfinder with no picture in it: four corner marks round [area] (root px) and a cross in its middle, in hairline
 * grey, drawn in the [frame]'s own coordinates.
 */
@Composable
private fun BoxScope.Viewfinder(area: Rect, frame: Rect) {
    Box(
        Modifier.matchParentSize().drawWithCache {
            // Built when the size or the area changes, not on every draw.
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

/** Why the camera or the last take failed, over the picture. */
@Composable
internal fun Banners(camera: CameraState, saveError: UiText?) {
    WarningBanner(camera.error?.text())
    WarningBanner(saveError?.text())
}

/** A warning that opens over the picture's edge and closes up again, still saying what it said while it goes. */
@Composable
private fun WarningBanner(text: String?) {
    var last by remember { mutableStateOf(text) }
    if (text != null) last = text
    AnimatedVisibility(
        text != null,
        enter = CgMotion.reveal,
        exit = CgMotion.conceal,
    ) {
        last?.let { Banner(it, BannerKind.Warning) }
    }
}

/** A cover screen or a sliver of split screen: the clock over the picture and the button under it, nothing else. */
@Composable
private fun BoxScope.TinyControls(state: RecordUiState, actions: RecordActions) {
    // Read while drawing: the change of colour redraws the chip, it does not recompose it.
    val ground =
        animateColorAsState(if (state.recording) CgColors.Status.Record else CgColors.Surface.Raised, CgMotion.medium())
    Text(
        if (state.recording) state.status.text() else stringResource(Res.string.record_ready),
        style = CgTheme.type.label,
        color = CgColors.Text.Primary,
        modifier =
            Modifier.align(Alignment.TopCenter)
                .padding(top = CgSpace.S24)
                .drawBehind { drawRect(ground.value) }
                .padding(horizontal = CgSpace.S14, vertical = CgSpace.S6),
    )
    RecordButton(
        state =
            when {
                state.recording -> RecordButtonState.Recording
                state.noCamera -> RecordButtonState.Disabled
                else -> RecordButtonState.Armed
            },
        onClick = actions::recordPressed,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = CgSpace.S24).testTag(TestTags.RecordButton),
    )
}

/** What the focus label's padding grows by, to a 44 dp target, with its words where they were. */
private val FOCUS_LABEL_GROW = 2.dp

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
    val gap = (FOCUS_RING_RADIUS + 4).dp.roundToPx() - grow.roundToPx()
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = (ring.x.toInt() - label.width / 2).coerceIn(0, maxOf(0, constraints.maxWidth - label.width))
        val below = ring.y.toInt() + gap
        val y = if (below + label.height <= constraints.maxHeight) below else ring.y.toInt() - gap - label.height
        label.place(x, y.coerceAtLeast(0))
    }
}

/**
 * What the camera pane draws from, read afresh by the one pane every layout shares. A data class: the same inputs again
 * (a recomposition above for something else) do not redraw the camera pane and its graph.
 */
private data class PaneInputs(
    val state: RecordUiState,
    val camera: CameraState,
    val settings: RecordingSettings,
    val granted: Boolean,
    val onAllowCamera: () -> Unit,
    val allowCameraLabel: String,
    val graphStyle: GraphStyle,
    val actions: RecordActions,
    val sampleLuma: (x: Float, y: Float, w: Float, h: Float) -> Float?,
    val picture: @Composable (Modifier, drawFrames: Boolean) -> Unit,
    val capture: VideoFormat,
)

/** Every sheet on a tablet, at most. */
private val SHEET_MAX_WIDTH = 640.dp

/** How far out a graph may stand before it is moved in: what moving it between frames rounds off. */
private const val BOUNDS_SLACK = 0.005f

/** The camera's error as the banner says it. */
@Composable
private fun CameraError.text(): String =
    when (this) {
        CameraError.NoRearCamera -> stringResource(Res.string.camera_no_rear)
        CameraError.NoSpace -> stringResource(Res.string.camera_no_space)
        is CameraError.RecordingFailed -> detail ?: stringResource(Res.string.camera_recording_failed)
    }

/** A small key that slides out of its row's edge and back into it, rather than appearing and vanishing. */
@Composable
internal fun KeyIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = expandHorizontally(CgMotion.sizeSpec, expandFrom = Alignment.End),
        exit = shrinkHorizontally(CgMotion.sizeSpec, shrinkTowards = Alignment.End),
    ) {
        content()
    }
}
