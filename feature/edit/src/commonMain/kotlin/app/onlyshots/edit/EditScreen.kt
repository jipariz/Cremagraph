package app.onlyshots.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.onlyshots.StyleEditorRoute
import app.onlyshots.core.designsystem.component.CgDialog
import app.onlyshots.core.designsystem.component.DialogRisk
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_gallery_access
import app.onlyshots.core.resources.edit_keep_editing
import app.onlyshots.core.resources.edit_leave
import app.onlyshots.core.resources.edit_leave_text
import app.onlyshots.core.resources.edit_leave_title
import app.onlyshots.core.resources.text
import app.onlyshots.export.ExportManager
import app.onlyshots.platform.rememberGalleryAccess
import app.onlyshots.platform.rememberShotFilePicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The editor, laid out as the web editor's phone layout: chips for the shot and the export along the top, the picture
 * filling the screen, and the timeline band under it; everything else in sheets that leave the picture in view.
 *
 * Tap the picture to play or pause; tap the graph to select it, which brings up its outline, its grip and a toolbar for
 * style, data, transparency and a reset. Tap the picture again to put the graph or a sheet down.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditScreen(
    id: String,
    onBack: () -> Unit,
    onExported: () -> Unit,
    onStyleEditor: (StyleEditorRoute) -> Unit = {},
    savedStyle: StateFlow<String?> = MutableStateFlow(null),
    onSavedStyleUsed: () -> Unit = {},
    /** Whether leaving a video that was never saved or shared asks first: on a phone it cannot be opened again. */
    askBeforeLeaving: Boolean = true,
    /** Opens the phones' machine shot picker; null where the host cannot reach machines (desktop, web). */
    onFromMachine: (() -> Unit)? = null,
    /** A shot the machine shot picker loaded, as JSON, to attach; [onPickedShotUsed] once it is. */
    pickedShot: StateFlow<String?> = MutableStateFlow(null),
    onPickedShotUsed: () -> Unit = {},
    viewModel: EditViewModel = koinViewModel { parametersOf(id) },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val customStyles by viewModel.styles.customStyles.collectAsStateWithLifecycle(viewModel.styles.custom)
    // A style just saved in the style editor becomes this video's style.
    val justSaved by savedStyle.collectAsStateWithLifecycle()
    LaunchedEffect(justSaved) {
        justSaved?.let {
            viewModel.pickStyle(it)
            onSavedStyleUsed()
        }
    }
    // A shot the machine shot picker loaded: the view model attaches it once the video is in.
    val picked by pickedShot.collectAsStateWithLifecycle()
    LaunchedEffect(picked) {
        picked?.let {
            viewModel.attachPickedShot(it)
            onPickedShotUsed()
        }
    }
    val player = remember { PlayerState() }
    // A Gaggiuino shot the machine uploaded on its own: SproFiler, the visualizer and the machine's own API all hand
    // out the same file.
    val pickShotFile = rememberShotFilePicker(viewModel::importShotFile)
    val video = ui.item
    val sound = playsSound(player.muted, video)
    // The gallery may need a permission first (Photos on iOS, storage on old Android): asked on the tap, not before.
    val askGallery = rememberGalleryAccess { granted ->
        if (granted) viewModel.saveAndExport(ui.galleryQuality, sound, ExportManager.Destination.GALLERY)
        else viewModel.showMessage(Res.string.edit_gallery_access.text())
    }
    // The playhead starts where the export does, and stays inside the trim when the trim moves past it.
    LaunchedEffect(video?.trimInUs, video?.trimOutUs) {
        val v = video ?: return@LaunchedEffect
        if (player.positionUs !in v.trimInUs..v.trimOutUs)
            player.seek(player.positionUs.coerceIn(v.trimInUs, v.trimOutUs))
    }

    // There is no library to come back to: leaving a video that was never saved or shared asks first, from the back
    // button and the system back alike. An open sheet is put down before anything else.
    var askLeave by remember { mutableStateOf(false) }
    val leave = { if (ui.kept || !askBeforeLeaving) onBack() else askLeave = true }
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None)) {
        if (ui.sheet != null && ui.exporting == null) viewModel.closeSheet() else leave()
    }
    if (askLeave) {
        CgDialog(
            title = stringResource(Res.string.edit_leave_title),
            text = stringResource(Res.string.edit_leave_text),
            onDismiss = { askLeave = false },
            confirm = stringResource(Res.string.edit_keep_editing) to { askLeave = false },
            risk = DialogRisk.Dismiss,
            dismiss =
                stringResource(Res.string.edit_leave) to
                    {
                        askLeave = false
                        onBack()
                    },
        )
    }

    // The sheet closes as the picker opens, so coming back shows the video with its new shot.
    val fromMachine = onFromMachine?.let { open ->
        {
            viewModel.closeSheet()
            open()
        }
    }
    CompositionLocalProvider(LocalFromMachine provides fromMachine) {
        EditBody(
            ui = ui,
            customStyles = customStyles,
            player = player,
            actions = viewModel,
            onBack = leave,
            onStyleEditor = onStyleEditor,
            onExported = onExported,
            onPickShotFile = pickShotFile,
            onSaveToGallery = askGallery,
            picture = { modifier ->
                val v = ui.item
                val path = viewModel.videoPath
                if (v != null && path != null) {
                    VideoSurface(
                        file = path,
                        state = player,
                        clipUs = v.trimInUs..v.trimOutUs,
                        drawFrames = ui.style?.blursVideo == true,
                        modifier = modifier,
                    )
                }
            },
        )
    }
}
