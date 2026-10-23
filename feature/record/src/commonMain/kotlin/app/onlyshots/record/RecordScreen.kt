package app.onlyshots.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.StyleEditorRoute
import app.onlyshots.camera.CameraPreview
import app.onlyshots.core.resources.text
import app.onlyshots.platform.KeepScreenOn
import app.onlyshots.platform.LockOrientation
import app.onlyshots.platform.rememberCameraGranted
import app.onlyshots.platform.rememberMediaPermission
import app.onlyshots.platform.rememberOpenAppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * The record screen: the camera armed with the source's live graph over it. Asks for the camera and the microphone,
 * re-arms on coming back, and opens each finished take in the editor ([onSaved]); [RecordBody] draws it.
 */
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
    var permission by remember { mutableStateOf(CameraPermission()) }
    val askMedia = rememberMediaPermission { camera, mic ->
        permission = permission.answer(camera, mic)
        viewModel.cameraAccess(camera)
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
        when (permission.onStart(cameraAllowedNow)) {
            CameraPermission.OnStart.Arm -> viewModel.arm(permission.mic)
            CameraPermission.OnStart.AskAgain -> askMedia()
            CameraPermission.OnStart.Nothing -> Unit
        }
    }
    KeepScreenOn(state.phase == RecordPhase.ARMED || state.recording)
    // The camera films its whole frame the way the screen stands: 9:16 upright, 16:9 on its side. Turned while armed,
    // the pre-roll starts again the new way; while a take runs the screen holds still, and asks again once it ends.
    // Measured here rather than read from the window's info, which a rotation handled in place can leave stale.
    var size by remember { mutableStateOf(IntSize.Zero) }
    val standing = formatThatFits(size.width, size.height)
    LaunchedEffect(standing, state.recording) { if (size != IntSize.Zero) viewModel.capture(standing, permission.mic) }
    val capture by viewModel.captureFormat.collectAsStateWithLifecycle()
    if (state.recording) LockOrientation(landscape = capture.landscape)
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    // Kept as state and read only by the tiles that show it: a new sample redraws them, not the camera and its graph.
    val live = viewModel.live.collectAsStateWithLifecycle()
    // Read only where the graph is drawn.
    val luma = viewModel.backdropLuma.collectAsStateWithLifecycle()
    RecordBody(
        modifier = Modifier.onSizeChanged { size = it },
        state = state,
        camera = camera,
        settings = settings,
        graphStyle = viewModel.style(settings.overlay, state.sourceKind),
        customStyles = customStyles,
        granted = permission.camera,
        canSimulateShot = viewModel.canSimulateShot,
        onAllowCamera = {
            when (permission.allowCamera) {
                CameraPermission.AllowCamera.OpenSettings -> openSettings()
                CameraPermission.AllowCamera.Ask -> {
                    permission = permission.askingFromButton()
                    askMedia()
                }
            }
        },
        allowCameraLabel = permission.allowCameraLabel.text(),
        saveError = saveError,
        actions = viewModel,
        onBack = onBack,
        onStyleEditor = onStyleEditor,
        capture = capture,
        live = { live.value },
        backdropLuma = { luma.value },
        picture = { modifier, drawFrames -> CameraPreview(viewModel.camera, modifier, drawFrames = drawFrames) },
    )
}
