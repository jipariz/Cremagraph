package app.onlyshots.record

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.StyleEditorRoute
import app.onlyshots.camera.CameraError
import app.onlyshots.camera.CameraPreview
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.Banner
import app.onlyshots.core.designsystem.component.BannerKind
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.EditorSheet
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.RecordButton
import app.onlyshots.core.designsystem.component.RecordButtonState
import app.onlyshots.core.designsystem.component.RulesStrip
import app.onlyshots.core.designsystem.component.RulesStripHint
import app.onlyshots.core.designsystem.component.StatusChip
import app.onlyshots.core.designsystem.component.TwoPane
import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Reframe
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.formatClock
import app.onlyshots.core.model.heldWrongWay
import app.onlyshots.core.model.sourcePoint
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
import app.onlyshots.core.resources.record_no_data_hint
import app.onlyshots.core.resources.record_open_settings
import app.onlyshots.core.resources.record_ready
import app.onlyshots.core.resources.record_settings
import app.onlyshots.core.resources.record_simulate_shot
import app.onlyshots.core.resources.record_style
import app.onlyshots.core.resources.record_title
import app.onlyshots.core.resources.record_turn_sideways_hint
import app.onlyshots.core.resources.record_turn_upright_hint
import app.onlyshots.core.resources.text
import app.onlyshots.edit.DataSheetContent
import app.onlyshots.edit.EditSheet
import app.onlyshots.edit.GraphToolbar
import app.onlyshots.edit.OverlayEditLayer
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.layout.WidePanes
import app.onlyshots.machineLabel
import app.onlyshots.overlay.compose.ShotOverlay
import app.onlyshots.platform.KeepScreenOn
import app.onlyshots.platform.LockOrientation
import app.onlyshots.platform.rememberCameraGranted
import app.onlyshots.platform.rememberMediaPermission
import app.onlyshots.platform.rememberOpenAppSettings
import app.onlyshots.style.StylePicker
import app.onlyshots.style.StylePickerActions
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    // The screen stands the way the video is filmed: the camera records the way the screen stands. A new format starts
    // the pre-roll again, the new way, once the screen has turned.
    LockOrientation(landscape = settings.format.landscape)
    // Where the lock does not hold (a foldable's inner display ignores it), the camera waits until the screen is
    // turned.
    val window = LocalWindowInfo.current.containerSize
    val wrongWay = heldWrongWay(window.width, window.height, settings.format)
    LaunchedEffect(wrongWay, cameraGranted) { viewModel.heldWrongWay(wrongWay, micGranted) }
    var armedFormat by remember { mutableStateOf(settings.format) }
    LaunchedEffect(settings.format) {
        if (settings.format != armedFormat) {
            armedFormat = settings.format
            if (cameraGranted) viewModel.rearm(micGranted)
        }
    }
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val takeFormat by viewModel.takeFormat.collectAsStateWithLifecycle()
    // A book foldable resting on its side films 16:9 this take, unless told to keep the tall format.
    val onItsSide = LocalWindowLayout.current.let { it.kind == LayoutKind.Stacked && it.onItsSide }
    LaunchedEffect(onItsSide) { viewModel.onItsSide(onItsSide) }
    val savedFormat by viewModel.savedFormat.collectAsStateWithLifecycle()
    RecordBody(
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
        turnHint = if (wrongWay) turnHint(settings.format) else null,
        live = live,
        switchedFrom = savedFormat.takeIf { takeFormat != null },
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
    /** Which way to turn the screen before anything is filmed, while it stands the other way from the format. */
    turnHint: String? = null,
    /** The source's newest reading, for the tiles before a take starts. */
    live: Sample? = null,
    /** The saved format this take leaves aside (on its side, a tall format records 16:9 this time). */
    switchedFrom: VideoFormat? = null,
) {
    val layout = LocalWindowLayout.current
    var settingsOpen by remember { mutableStateOf(false) }
    // The graph is edited as on the edit screen: tap it to select it, then its toolbar opens style and data.
    var graphSelected by remember { mutableStateOf(false) }
    var graphSheet by remember { mutableStateOf<EditSheet?>(null) }
    LaunchedEffect(state.recording, state.hasData) {
        if (state.recording || !state.hasData) {
            graphSelected = false
            graphSheet = null
        }
    }
    val header = @Composable { HeaderBack(title = stringResource(Res.string.record_title), onBack = onBack) }

    // One camera node for every layout: moving it between branches keeps the preview surface and
    // its binding alive, where re-creating it would black out the picture on every posture change.
    // The pane is created once, so it reads what changes through this rather than keeping what it first saw (the
    // camera permission arrives after the first frame, the session's state every tick).
    val inputs =
        rememberUpdatedState(
            PaneInputs(
                state,
                camera,
                settings,
                granted,
                graphStyle,
                actions,
                sampleLuma,
                picture,
                turnHint,
                onAllowCamera,
                allowCameraLabel,
            )
        )
    val cameraPane = remember {
        movableContentOf { modifier: Modifier ->
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
                turnHint = now.turnHint,
                graphSelected = graphSelected,
                onGraphSelected = { graphSelected = it },
                onGraphSheet = { graphSheet = it },
                onSettings = { settingsOpen = true },
                modifier = modifier,
            )
        }
    }
    // The graph's style and data: a bottom sheet on a phone, the side panel on a wide window.
    val graphSheetBody: @Composable (EditSheet) -> Unit = { sheet ->
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
            DataSheetContent(settings.overlay, Capabilities.of(state.sourceKind, hasScale = true).series) { change ->
                actions.updateOverlay(change)
            }
        }
    }
    val take =
        TakeInputs(
            state = state,
            camera = camera,
            settings = settings,
            live = live,
            actions = actions,
            canSimulateShot = canSimulateShot,
            saveError = saveError,
            wrongWay = turnHint != null,
            switchedFrom = switchedFrom,
            onBack = onBack,
            onGraph = { graphSheet = EditSheet.STYLE }.takeIf { state.hasData && !state.recording },
            onSettings = { settingsOpen = true },
        )
    val window = LocalWindowInfo.current.containerSize
    // Every panel beside or under the camera: the format that fits while held the wrong way, else the posture's own.
    val controls =
        @Composable { modifier: Modifier, posture: @Composable (Modifier) -> Unit ->
            if (turnHint != null && !state.recording)
                WrongWayPanel(take, formatThatFits(window.width, window.height), modifier)
            else posture(modifier)
        }

    Box(Modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding()) {
        when (layout.kind) {
            // A flip's cover screen: the picture, the clock over it and the button under it, nothing else.
            LayoutKind.Tiny ->
                Box(Modifier.fillMaxSize()) {
                    cameraPane(Modifier.fillMaxSize())
                    TinyControls(state, actions)
                }

            // The camera beside a panel of fixed width: what the panel shows can change (the graph's style or data in
            // place of the controls) without resizing the preview, which would rebind the camera mid-recording.
            LayoutKind.Wide ->
                WidePanes(
                    layout,
                    main = {
                        Column(Modifier.fillMaxSize()) {
                            header()
                            cameraPane(Modifier.weight(1f).fillMaxWidth())
                        }
                    },
                    panel = {
                        val sheet = graphSheet
                        if (sheet == null) controls(Modifier) { RecordSidePanel(take, it) }
                        else
                            PanelPage(
                                stringResource(
                                    if (sheet == EditSheet.STYLE) Res.string.record_style else Res.string.record_data
                                ),
                                onBack = { graphSheet = null },
                            ) {
                                graphSheetBody(sheet)
                            }
                    },
                )

            LayoutKind.Stacked ->
                TwoPane(
                    vertical = true,
                    splitDp = layout.splitDp.dp,
                    hingeDp = layout.hingeDp.dp,
                    first = { cameraPane(Modifier.fillMaxSize()) },
                    second = {
                        Box(Modifier.fillMaxSize().background(CgColors.Surface.Bar)) {
                            controls(Modifier) {
                                if (layout.onItsSide) TabletopPanel(take, it) else FlexPanel(take, it)
                            }
                        }
                    },
                )

            LayoutKind.Phone ->
                Column(Modifier.fillMaxSize()) {
                    header()
                    // Everything around the picture keeps its size, and banners float over it: resizing the preview
                    // rebinds the camera, which would end the recording.
                    Box(Modifier.weight(1f)) {
                        cameraPane(Modifier.fillMaxSize())
                        Column(Modifier.align(Alignment.BottomCenter)) { Banners(camera, saveError) }
                    }
                    RecordControls(
                        state,
                        settings,
                        actions,
                        canSimulateShot,
                        onSettings = { settingsOpen = true },
                    )
                }
        }
    }

    graphSheet
        ?.takeIf { layout.kind != LayoutKind.Wide }
        ?.let { sheet ->
            // The graph for this and the next recordings; a recorded video keeps its own copy, edited in the editor.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                EditorSheet(
                    title =
                        stringResource(
                            if (sheet == EditSheet.STYLE) Res.string.record_style else Res.string.record_data
                        ),
                    onDone = { graphSheet = null },
                    modifier = Modifier.fillMaxHeight(0.72f).wrapContentHeight(Alignment.Bottom),
                ) {
                    graphSheetBody(sheet)
                }
            }
        }

    if (settingsOpen) {
        RecordingSheet(
            settings = settings,
            sourceKind = state.sourceKind,
            onChange = actions::updateSettings,
            onDismiss = { settingsOpen = false },
        )
    }
}

/**
 * The picture and the overlay. The picture is what the camera records: 9:16, or 16:9 with the phone turned. A 4:5 or
 * 1:1 format keeps its middle, so the rest is dimmed and the graph lives in that frame, as it will in the video.
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
    turnHint: String?,
    graphSelected: Boolean,
    onGraphSelected: (Boolean) -> Unit,
    onGraphSheet: (EditSheet) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = settings.format
    // What the camera records, and where the video's frame sits in it.
    val captureRatio = if (format.landscape) 16f / 9f else 9f / 16f
    // On a cover screen or a sliver of split screen there is room for the picture and nothing
    // else: the chip wraps to six lines and the graph card is smaller than its own labels.
    val tiny = LocalWindowLayout.current.kind == LayoutKind.Tiny
    // The camera picture is Haze's source, so styles that blur what is behind the graph blur the live picture.
    val haze = rememberHazeState()
    // A style that adapts to the video follows how bright the picture is behind the graph, sampled twice a second and
    // smoothed so it does not flicker.
    var luma by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(graphStyle.adapt.enabled, settings.overlay.rect, format, granted) {
        if (!graphStyle.adapt.enabled || !granted) {
            luma = null
            return@LaunchedEffect
        }
        // The graph's rect is in the video's frame; the camera is measured in its own, around it.
        val r = settings.overlay.rect
        val (x0, y0) = sourcePoint(r.x, r.y, captureRatio, format, Reframe())
        val (x1, y1) = sourcePoint(r.right, r.bottom, captureRatio, format, Reframe())
        while (true) {
            sampleLuma(x0, y0, x1 - x0, y1 - y0)?.let { now -> luma = luma?.let { it + (now - it) * 0.5f } ?: now }
            delay(500)
        }
    }
    // Where the last tap focused, and a count so every tap restarts the ring.
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusTaps by remember { mutableStateOf(0) }
    // Bumped by a pinch, so the zoom track shows while the picture is being zoomed.
    var pinches by remember { mutableStateOf(0) }
    val zoom by rememberUpdatedState(camera.zoom)
    val selected by rememberUpdatedState(graphSelected)
    val recording by rememberUpdatedState(state.recording)
    LaunchedEffect(camera.focusLocked) { if (!camera.focusLocked) focusAt = null }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Height first: in a side pane the picture has to fit the pane, not fill its width.
        Box(Modifier.aspectRatio(captureRatio, matchHeightConstraintsFirst = true).background(CgColors.Surface.Scrim)) {
            if (granted) {
                // PreviewView scales its surface to fill and does not clip it: without this the
                // picture spills past the 9:16 frame and over whatever shares the window.
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
                            // Pinch to zoom, as in the camera app; not mid-shot, where the rail is hidden too.
                            detectTransformGestures { _, _, pinch, _ ->
                                if (!recording && pinch != 1f) {
                                    actions.setZoom(zoom * pinch)
                                    pinches++
                                }
                            }
                        }
                )
                focusAt?.let { FocusRing(it, focusTaps) }
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(CgSpace.S24),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
                ) {
                    Text(
                        stringResource(Res.string.record_needs_camera),
                        style = CgTheme.type.caption,
                        color = CgColors.Text.Secondary,
                        textAlign = TextAlign.Center,
                    )
                    CgButton(allowCameraLabel, onClick = onAllowCamera)
                }
            }
            // The video's frame: all of the picture at 9:16 and 16:9, its middle at 4:5 and 1:1, the rest dimmed.
            if (format.ratio != captureRatio) CropMask(format.ratio, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(format.ratio)) {
                // Without a machine there is nothing to draw: the screen is a camera, and the shot comes in the editor.
                if (!tiny && state.hasData)
                    ShotOverlay(
                        shot = state.shot,
                        videoTimeUs = state.videoTimeUs,
                        shotStartUs = state.shotStartInVideoUs,
                        settings = settings.overlay.forSource(state.sourceKind),
                        // From the state, not the view model: the machine can change underneath.
                        style = graphStyle,
                        title =
                            "${state.shot.profileName.ifEmpty { RecordSession.defaultProfileName(state.sourceKind).text() }} · " +
                                machineLabel(state.sourceKind),
                        fontFamily = CgTheme.overlayFont,
                        modifier = Modifier.fillMaxSize(),
                        hazeState = haze,
                        backdropLuma = luma,
                    )
                // Placed by hand before a shot; while recording nothing on the picture moves.
                if (!tiny && !state.recording && state.hasData)
                    OverlayEditLayer(
                        rect = settings.overlay.rect,
                        onChange = { rect, _ -> actions.updateOverlay { it.copy(rect = rect) } },
                        onTap = { onGraphSelected(!graphSelected) },
                        selected = graphSelected,
                        leftGrip = true,
                        format = format,
                        toolbar = {
                            GraphToolbar(
                                opacity = settings.overlay.opacity,
                                onStyle = { onGraphSheet(EditSheet.STYLE) },
                                onData = { onGraphSheet(EditSheet.DATA) },
                                onOpacity = { o -> actions.updateOverlay { it.withOpacity(o) } },
                                onReset = { actions.updateOverlay { it.copy(rect = NormRect.default(format)) } },
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                // Waiting to be turned, the camera is not armed whatever the session says; the hint says what to do.
                if (!tiny && (turnHint == null || state.recording))
                    StatusChip(
                        text = state.status.text(),
                        recording = state.recording,
                        dot = if (state.recording) CgColors.Text.Primary else null,
                        // The opposite corner from the overlay, which it must never cover. At the top it also keeps
                        // clear of the format pill in the other corner, wrapping rather than running under it.
                        modifier =
                            Modifier.align(
                                    if (settings.overlay.rect.y < 0.5f) Alignment.BottomStart else Alignment.TopStart
                                )
                                .padding(CgSpace.S12)
                                .padding(end = if (settings.overlay.rect.y < 0.5f) 0.dp else PILL_CLEARANCE),
                    )
            }
            if (!state.recording && turnHint != null) TurnOverlay(format)
            if (!state.recording && !tiny) {
                // The corner the status chip never takes, which on a narrow frame would cover a pill in the middle.
                Pill(
                    format.label,
                    onClick = onSettings,
                    modifier = Modifier.align(Alignment.TopEnd).padding(CgSpace.S12),
                )
            }
            if (!state.recording && granted && !tiny) {
                CameraRail(
                    state = camera,
                    onZoom = actions::setZoom,
                    reveal = pinches,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(CgSpace.S12),
                )
            }
            val ring = focusAt
            if (camera.focusLocked && ring != null && granted && !tiny && !state.recording) {
                // Under the ring, as the camera apps put their lock label: tap it to go back to continuous focus
                // and exposure. A held focus survives into the recording, as it should.
                Pill(
                    stringResource(Res.string.record_focus_locked),
                    onClick = actions::unlockFocus,
                    color = FocusYellow,
                    modifier = Modifier.belowFocusRing(ring),
                )
            }
        }
    }
}

/**
 * The pane beside or below the picture when there is room: live readings while a shot runs, and what the next recording
 * will do while it does not.
 */
@Composable
internal fun Banners(camera: CameraState, saveError: UiText?) {
    camera.error?.let { Banner(it.text(), BannerKind.Warning) }
    saveError?.let { Banner(it.text(), BannerKind.Warning) }
}

@Composable
private fun RecordControls(
    state: RecordUiState,
    settings: RecordingSettings,
    actions: RecordActions,
    canSimulateShot: Boolean,
    onSettings: () -> Unit,
) {
    val button =
        @Composable {
            RecordButton(
                state =
                    when {
                        state.phase == RecordPhase.RECORDING -> RecordButtonState.Recording
                        state.noCamera -> RecordButtonState.Disabled
                        state.phase == RecordPhase.ARMED -> RecordButtonState.Armed
                        else -> RecordButtonState.Idle
                    },
                onClick = actions::recordPressed,
            )
        }
    val simulate = canSimulateShot && state.phase == RecordPhase.ARMED && !state.noCamera
    // The rules and their settings need a machine, and the settings are closed mid-shot. The strip stays while
    // recording
    // (the rules still apply) and keeps its slot without a machine, so nothing under the picture changes height.
    val showGear = state.hasData && !state.recording
    val gear =
        @Composable {
            IconCircleButton(CgIcons.Settings, stringResource(Res.string.record_settings), onClick = onSettings)
        }
    val clock = @Composable { style: TextStyle -> Text(formatClock(state.videoTimeUs), style = style) }
    Column(Modifier.fillMaxWidth().background(CgColors.Surface.Bar)) {
        if (state.hasData) {
            val (start, startDetail) = RecordSession.startRuleShort(settings)
            RulesStrip(
                start.text(),
                startDetail?.text(),
                RecordSession.stopRuleShort(settings.effectiveFor(state.sourceKind).stopRule).text(),
            )
        } else {
            RulesStripHint(stringResource(Res.string.record_no_data_hint))
        }
        Row(
            Modifier.fillMaxWidth().height(CgSize.Record + CgSpace.S16 * 2).padding(horizontal = CgSpace.S16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                when {
                    state.recording -> clock(CgTheme.type.cardTitle)
                    simulate ->
                        CgButton(
                            stringResource(Res.string.record_simulate_shot),
                            onClick = actions::simulateShot,
                            kind = CgButtonKind.Secondary,
                        )
                }
            }
            button()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { if (showGear) gear() }
        }
    }
}

/** A cover screen or a sliver of split screen: the clock over the picture and the button under it, nothing else. */
@Composable
private fun BoxScope.TinyControls(state: RecordUiState, actions: RecordActions) {
    Text(
        if (state.recording) formatClock(state.videoTimeUs) else stringResource(Res.string.record_ready),
        style = CgTheme.type.cardTitle,
        modifier =
            Modifier.align(Alignment.TopCenter)
                .padding(top = CgSpace.S24)
                .clip(CgShape.Pill)
                .background(if (state.recording) CgColors.Status.RecordingChip else CgColors.Surface.Raised)
                .padding(horizontal = CgSpace.S14, vertical = CgSpace.S4),
    )
    RecordButton(
        state =
            when {
                state.recording -> RecordButtonState.Recording
                state.noCamera -> RecordButtonState.Disabled
                else -> RecordButtonState.Armed
            },
        onClick = actions::recordPressed,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = CgSpace.S24),
    )
}

/**
 * Places a label centred under the focus ring at [ring], or over it when there is no room below, kept inside the
 * picture.
 */
private fun Modifier.belowFocusRing(ring: Offset): Modifier = layout { measurable, constraints ->
    val label = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val gap = (FOCUS_RING_RADIUS + 4).dp.roundToPx()
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = (ring.x.toInt() - label.width / 2).coerceIn(0, maxOf(0, constraints.maxWidth - label.width))
        val below = ring.y.toInt() + gap
        val y = if (below + label.height <= constraints.maxHeight) below else ring.y.toInt() - gap - label.height
        label.place(x, y.coerceAtLeast(0))
    }
}

/** A page the wide panel shows in place of the controls, with the way back to them. */
@Composable
private fun PanelPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(CgSpace.S16), verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
            BackButton(onClick = onBack)
            Text(title, style = CgTheme.type.sheetTitle)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            content()
        }
    }
}

/** The area outside a centred [ratio] frame (width over height), dimmed, and the frame's edge. */
@Composable
private fun CropMask(ratio: Float, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val h = size.width / ratio
        val top = (size.height - h) / 2f
        val dim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)
        drawRect(dim, size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(
            dim,
            topLeft = Offset(0f, top + h),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - top - h),
        )
        drawRect(
            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, h),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
        )
    }
}

/** What the camera pane draws from, read afresh by the one pane every layout shares. */
private class PaneInputs(
    val state: RecordUiState,
    val camera: CameraState,
    val settings: RecordingSettings,
    val granted: Boolean,
    val graphStyle: GraphStyle,
    val actions: RecordActions,
    val sampleLuma: (x: Float, y: Float, w: Float, h: Float) -> Float?,
    val picture: @Composable (Modifier, drawFrames: Boolean) -> Unit,
    val turnHint: String?,
    val onAllowCamera: () -> Unit,
    val allowCameraLabel: String,
)

/** What to do when the screen stands the other way from [format]. */
@Composable
private fun turnHint(format: VideoFormat): String =
    stringResource(
        if (format.landscape) Res.string.record_turn_sideways_hint else Res.string.record_turn_upright_hint,
        format.label,
    )

/** Room the status chip leaves at the top for the format pill in the corner opposite. */
private val PILL_CLEARANCE = 64.dp

/** The camera's error as the banner says it. */
@Composable
private fun CameraError.text(): String =
    when (this) {
        CameraError.NoRearCamera -> stringResource(Res.string.camera_no_rear)
        CameraError.NoSpace -> stringResource(Res.string.camera_no_space)
        is CameraError.RecordingFailed -> detail ?: stringResource(Res.string.camera_recording_failed)
    }
