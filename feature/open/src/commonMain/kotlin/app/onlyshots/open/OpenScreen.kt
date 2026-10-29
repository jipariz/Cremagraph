package app.onlyshots.open

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.component.BarIcon
import app.onlyshots.core.designsystem.component.BarPriority
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.designsystem.component.NativeBarItem
import app.onlyshots.core.designsystem.component.NativeItems
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.open_editor
import app.onlyshots.core.resources.open_from_file
import app.onlyshots.core.resources.open_sheet_title
import app.onlyshots.core.resources.visualizer_from
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.platform.rememberShotFilePicker
import app.onlyshots.platform.rememberVideoPicker
import app.onlyshots.platform.videoDrop
import app.onlyshots.visualizer.VisualizerFetchForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every editor's start: pick the video and its shot, then open the editor, which lines the shot up. Both are needed:
 * the editor is for putting a shot's graph on a video. The shot comes from a `.shot` or `.json` file, from
 * visualizer.coffee by a share code or link, or from the machine itself where the host can reach one ([onFromMachine]:
 * the phones and the desktop, not the web), whose picker hands the shot back through [pickedShot].
 *
 * The phones come here from their start screen ([OpenEntry.PhoneApp]); the desktop and the web start here and take a
 * dropped video ([OpenEntry.Desk]).
 */
@Composable
fun OpenScreen(
    onOpen: (id: String) -> Unit,
    entry: OpenEntry,
    onFromMachine: (() -> Unit)? = null,
    pickedShot: StateFlow<String?> = MutableStateFlow(null),
    onPickedShotUsed: () -> Unit = {},
    viewModel: OpenViewModel = koinViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    val shot by viewModel.shot.collectAsStateWithLifecycle()
    val pickedFile by viewModel.pickedFile.collectAsStateWithLifecycle()
    val pickedThumb by viewModel.pickedThumb.collectAsStateWithLifecycle()
    val machineShot by pickedShot.collectAsStateWithLifecycle()
    // The machine's picker hands its shot back once: the view model takes it, and the hand-over is cleared.
    LaunchedEffect(machineShot) {
        val json = machineShot ?: return@LaunchedEffect
        viewModel.chooseMachineShot(json)
        onPickedShotUsed()
    }
    val videoPicker = rememberVideoPicker(viewModel::pickVideo)
    val shotFilePicker = rememberShotFilePicker(viewModel::chooseFile)
    val visualizerFetch by viewModel.visualizerFetch.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ShotSheet?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val machineReachable = onFromMachine != null
    val actions =
        remember(viewModel, videoPicker) {
            object : OpenActions {
                override fun pickVideo() = videoPicker()

                override fun pickShot() {
                    sheet = ShotSheet.Choices
                }

                override fun openEditor() = viewModel.openWith { currentOnOpen(it) }
            }
        }
    // Where the bars are native (iPhone Duo): open the editor (prominent) and, at the far end, a shot from a file.
    val openTitle = stringResource(Res.string.open_editor)
    val fileTitle = stringResource(Res.string.open_from_file)
    NativeItems(
        listOf(
            NativeBarItem(
                TestTags.OpenEditor,
                openTitle,
                BarIcon.Open,
                prominent = true,
                enabled = picked != null && shot != null,
                priority = BarPriority.High,
            ),
            NativeBarItem(TestTags.OpenFromFile, fileTitle, BarIcon.File, bottom = true, priority = BarPriority.Low),
        )
    ) { id ->
        if (id == TestTags.OpenEditor) actions.openEditor() else shotFilePicker()
    }
    Box(Modifier.fillMaxSize()) {
        OpenBody(
            ui =
                OpenUi(
                    picked = picked,
                    pickedFile = pickedFile?.copy(thumb = pickedThumb),
                    shot = shot,
                    status = status,
                    dragging = dragging,
                    fromMachine = machineReachable,
                ),
            entry = entry,
            actions = actions,
            modifier = Modifier.videoDrop(onFile = viewModel::pickVideo, onHover = { dragging = it }),
        )
        sheet?.let { page ->
            ShotSourceSheet(
                entry,
                page,
                onFile = {
                    sheet = null
                    shotFilePicker()
                },
                onMachine =
                    onFromMachine?.let { open ->
                        {
                            sheet = null
                            open()
                        }
                    },
                onVisualizer = { sheet = ShotSheet.Visualizer },
                visualizer = {
                    VisualizerFetchForm(
                        visualizerFetch,
                        onFetch = { viewModel.fetchFromVisualizer(it) { sheet = null } },
                    )
                },
                onDismiss = {
                    if (page == ShotSheet.Visualizer) viewModel.visualizerClosed()
                    sheet = null
                },
            )
        }
    }
}

/** The shot sheet's page: the ways to get a shot, or the Visualizer's code and link. */
private enum class ShotSheet {
    Choices,
    Visualizer,
}

/**
 * Where the shot comes from, asked in a sheet; on a fold, the sheet is the right page, over the picked video, and the
 * steps stay in view under a scrim.
 */
@Composable
private fun ShotSourceSheet(
    entry: OpenEntry,
    page: ShotSheet,
    onFile: () -> Unit,
    onMachine: (() -> Unit)?,
    onVisualizer: () -> Unit,
    visualizer: @Composable () -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = LocalWindowLayout.current
    val title =
        stringResource(if (page == ShotSheet.Visualizer) Res.string.visualizer_from else Res.string.open_sheet_title)
    val content: @Composable () -> Unit = {
        when (page) {
            ShotSheet.Choices -> ShotSourceChoices(onFile, onMachine, onVisualizer)
            ShotSheet.Visualizer -> visualizer()
        }
    }
    if (shotSheetIsFoldPage(entry, layout)) {
        FoldPageSheet(title, layout, onDismiss) { content() }
    } else {
        CgSheet(title = title, onDismiss = onDismiss) { content() }
    }
}
