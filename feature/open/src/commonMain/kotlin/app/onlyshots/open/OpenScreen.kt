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
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.open_sheet_title
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.platform.rememberShotFilePicker
import app.onlyshots.platform.rememberVideoPicker
import app.onlyshots.platform.videoDrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every editor's start: pick the video and its shot, then open the editor, which lines the shot up. Both are needed:
 * the editor is for putting a shot's graph on a video. The shot comes from a `.shot` file, or from the machine itself
 * where the host can reach one ([onFromMachine]: the phones and the desktop, not the web), whose picker hands the shot
 * back through [pickedShot].
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
    var choosingSource by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val machineReachable = onFromMachine != null
    val actions =
        remember(viewModel, videoPicker, shotFilePicker, machineReachable) {
            object : OpenActions {
                override fun pickVideo() = videoPicker()

                // With the file the only way (the web), the file picker opens at once rather than a sheet with one
                // choice.
                override fun pickShot() {
                    if (machineReachable) choosingSource = true else shotFilePicker()
                }

                override fun openEditor() = viewModel.openWith { currentOnOpen(it) }
            }
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
        if (choosingSource) {
            ShotSourceSheet(
                entry,
                onFile = {
                    choosingSource = false
                    shotFilePicker()
                },
                onMachine =
                    onFromMachine?.let { open ->
                        {
                            choosingSource = false
                            open()
                        }
                    },
                onDismiss = { choosingSource = false },
            )
        }
    }
}

/**
 * Where the shot comes from, asked in a sheet; on a fold, the sheet is the right page, over the picked video, and the
 * steps stay in view under a scrim.
 */
@Composable
private fun ShotSourceSheet(
    entry: OpenEntry,
    onFile: () -> Unit,
    onMachine: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val layout = LocalWindowLayout.current
    val title = stringResource(Res.string.open_sheet_title)
    if (shotSheetIsFoldPage(entry, layout)) {
        FoldPageSheet(title, layout, onDismiss) { ShotSourceChoices(onFile, onMachine) }
    } else {
        CgSheet(title = title, onDismiss = onDismiss) { ShotSourceChoices(onFile, onMachine) }
    }
}
