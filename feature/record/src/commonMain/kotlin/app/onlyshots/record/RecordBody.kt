package app.onlyshots.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import app.onlyshots.StyleEditorRoute
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.record_allow_camera
import app.onlyshots.edit.EditSheet
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import org.jetbrains.compose.resources.stringResource

/**
 * The record screen drawn from plain state: the session's [state], the [camera]'s, the recording [settings] and the
 * graph's resolved [graphStyle]; [actions] to ask for changes. [picture] draws the camera into the modifier it is given
 * (the platform preview in the app, a still in a screenshot). The window's layout picks one of the variants: a full
 * screen camera with floating controls, half folded, or a cover screen.
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
    onBack: () -> Unit,
    onStyleEditor: (StyleEditorRoute) -> Unit,
    picture: @Composable (Modifier, drawFrames: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Without the camera: asks for it again, or opens the system settings once asking no longer works. */
    onAllowCamera: () -> Unit = {},
    allowCameraLabel: String = stringResource(Res.string.record_allow_camera),
    /** The camera's whole frame as it films it: 9:16 with the screen upright, 16:9 on its side. */
    capture: VideoFormat = VideoFormat.PORTRAIT_9_16,
    /** The source's newest reading, for the tiles before a take starts; read where the tiles are, not here. */
    live: () -> Sample? = { null },
    /** How bright the picture is behind the graph, for styles that adapt; read where the graph is drawn. */
    backdropLuma: () -> Float? = { null },
    /** The graph selected from the start, its toolbar showing: for a screenshot. */
    graphSelectedAtFirst: Boolean = false,
) {
    val layout = LocalWindowLayout.current
    var settingsOpen by remember { mutableStateOf(false) }
    // The graph is edited as on the edit screen: tap it to select it, then its toolbar opens style and data.
    var graphSelected by remember { mutableStateOf(graphSelectedAtFirst) }
    var graphSheet by remember { mutableStateOf<EditSheet?>(null) }
    val endsGraphEditing = state.endsGraphEditing
    LaunchedEffect(endsGraphEditing) {
        if (endsGraphEditing) {
            graphSelected = false
            graphSheet = null
            settingsOpen = false
        }
    }
    val openRecordingSettings = remember {
        {
            graphSheet = null
            graphSelected = false
            settingsOpen = true
        }
    }
    // What of the window the floating controls leave free, in root px: the graph is kept there.
    var clear by remember { mutableStateOf<Rect?>(null) }
    // While the graph is moved the floating controls step aside, and it may go under them.
    var graphMoving by remember { mutableStateOf(false) }
    // The panels read only the take's rules, never the graph: a dragged graph changes the settings every frame, and
    // must not rebuild the floating controls with it.
    val rules =
        remember(settings.autoStart, settings.preRollSeconds, settings.stopRule) {
            RecordingSettings(settings.autoStart, settings.preRollSeconds, settings.stopRule)
        }
    val take =
        remember(state, camera, rules, live, actions, canSimulateShot, saveError, onBack) {
            TakeInputs(
                state = state,
                camera = camera,
                settings = rules,
                live = live,
                actions = actions,
                canSimulateShot = canSimulateShot,
                saveError = saveError,
                onBack = onBack,
                onSettings = openRecordingSettings,
            )
        }
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
                backdropLuma,
                picture,
                capture,
            )
        )
    val pane = remember {
        movableContentOf { modifier: Modifier, chrome: PaneChrome ->
            CameraPane(
                inputs = inputs.value,
                chrome = chrome,
                clear = { clear.takeIf { chrome == PaneChrome.Floating } },
                graph =
                    GraphSelection(
                        selected = graphSelected,
                        onSelected = { graphSelected = it },
                        onSheet = { graphSheet = it },
                        onMoving = { graphMoving = it },
                    ),
                modifier = modifier,
            )
        }
    }
    // The lenses' strip, wherever a layout puts it.
    val zoom: @Composable (Modifier, Boolean) -> Unit = { m, vertical ->
        if (state.showsLenses(granted)) LensPicker(camera, actions::setZoom, m, vertical)
    }

    Box(modifier.fillMaxSize().background(CgColors.Surface.Base)) {
        when (layout.kind) {
            LayoutKind.Tiny -> RecordTinyLayout(take, pane)
            LayoutKind.Stacked -> RecordStackedLayout(layout, take, pane, zoom)
            LayoutKind.Phone,
            LayoutKind.Wide ->
                RecordPhoneLayout(
                    layout = layout,
                    take = take,
                    placement = layout.controlsPlacement(capture),
                    graphOnTop = graphOnTop(settings.overlay.rect),
                    graphMoving = graphMoving,
                    pane = pane,
                    zoom = zoom,
                    onClear = { clear = it },
                )
        }
    }

    RecordSheets(
        RecordSheetInputs(state, settings, graphStyle, customStyles, actions, onStyleEditor),
        graphSheet = graphSheet,
        onGraphSheet = { graphSheet = it },
        settingsOpen = settingsOpen,
        onSettingsOpen = { settingsOpen = it },
        foldPage = layout.foldPageStartDp?.dp,
    )
}
