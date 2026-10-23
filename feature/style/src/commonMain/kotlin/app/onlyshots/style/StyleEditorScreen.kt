package app.onlyshots.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.SharedKeys
import app.onlyshots.core.designsystem.component.CgDialog
import app.onlyshots.core.designsystem.component.DialogRisk
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_discard
import app.onlyshots.core.resources.style_live_camera
import app.onlyshots.core.resources.style_save_ellipsis
import app.onlyshots.core.resources.style_save_this_style
import app.onlyshots.core.resources.style_unsaved_changes
import app.onlyshots.core.resources.style_your_video
import app.onlyshots.edit.PlayerState
import app.onlyshots.edit.VideoSurface
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The camera the record screen lends the style editor: its picture ([drawFrames] when a blur or glass style needs the
 * frames drawn by Compose) and the brightness of a rect of it, for adapt to video. A handle, so this module needs no
 * camera of its own.
 */
class LiveCamera(
    val sampleLuma: (x: Float, y: Float, w: Float, h: Float) -> Float?,
    val picture: @Composable (Modifier, drawFrames: Boolean) -> Unit,
)

/**
 * The picture under the preview while it is live: the camera (opened from the record screen) or the video playing
 * (opened from the editor), with how bright it is behind the graph, for adapt to video and the readability guard.
 */
class LivePicture(val caption: String, val luma: Float?, val content: @Composable (Modifier) -> Unit)

/**
 * Figma 13 · B–G: the graph style editor. A live preview: the camera when opened from the record screen, the video
 * playing when opened from the editor, else a frame of the user's newest video (drag to try other frames), tabs for
 * panel · lines · grid · text · readings, controls under them; every change shows at once. "save" names the style and
 * sets where it is the default; leaving with changes asks first.
 */
@Composable
fun StyleEditorScreen(
    baseId: String?,
    editId: String?,
    videoId: String?,
    onClose: (savedId: String?) -> Unit,
    /** Opened from the record screen: the live camera under the preview, lent by the host. */
    camera: LiveCamera? = null,
    viewModel: StyleEditorViewModel =
        koinViewModel(key = "style-$baseId-$editId-$videoId") {
            parametersOf(baseId.orEmpty(), editId.orEmpty(), videoId.orEmpty())
        },
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val frames by viewModel.frames.collectAsStateWithLifecycle()
    val video by viewModel.video.collectAsStateWithLifecycle()
    var saving by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    val live =
        when {
            camera != null -> liveCamera(draft, camera)
            else -> video?.let { liveVideo(it, draft, frames, viewModel) }
        }
    StyleEditorBody(
        draft = draft,
        frames = frames,
        live = live,
        sharedKey = (editId ?: baseId)?.let(SharedKeys::style),
        isNew = viewModel.isNew,
        onChange = viewModel::change,
        onBack = { if (viewModel.dirty) askDiscard = true else onClose(null) },
        onSave = { saving = true },
        sheetOverride =
            if (saving) {
                {
                    SaveSheet(
                        name = draft.name,
                        defaults = viewModel.defaults(),
                        onSave = { name, defaults -> onClose(viewModel.save(name, defaults)) },
                        onCancel = { saving = false },
                    )
                }
            } else null,
    )
    if (askDiscard) {
        CgDialog(
            title = stringResource(Res.string.style_save_this_style),
            text = stringResource(Res.string.style_unsaved_changes, draft.name),
            onDismiss = { askDiscard = false },
            confirm =
                stringResource(Res.string.style_save_ellipsis) to
                    {
                        askDiscard = false
                        saving = true
                    },
            dismiss = stringResource(Res.string.style_discard) to { onClose(null) },
            risk = DialogRisk.Dismiss,
        )
    }
}

/** The live camera, its brightness behind the preview's graph sampled and smoothed by [cameraLuma]. */
@Composable
private fun liveCamera(style: GraphStyle, camera: LiveCamera): LivePicture {
    val luma by
        remember(camera) {
                val r = PreviewRect
                cameraLuma { camera.sampleLuma(r.x, r.y, r.w, r.h) }
            }
            .collectAsState(initial = null)
    return LivePicture(stringResource(Res.string.style_live_camera), luma) { modifier ->
        camera.picture(modifier, style.blursVideo)
    }
}

/** The video the editor came from, playing silently on a loop inside its trim. */
@Composable
private fun liveVideo(
    video: VideoItem,
    style: GraphStyle,
    frames: List<PreviewFrame>,
    viewModel: StyleEditorViewModel,
): LivePicture {
    val player =
        remember(video.id) {
            PlayerState().apply {
                muted = true
                seek(video.trimInUs)
                playing = true
            }
        }
    // Which of the few frames is shown changes a dozen times a loop; the position, every frame: only the first
    // recomposes.
    val index by remember(video, player, viewModel) { derivedStateOf { viewModel.frameAt(video, player.positionUs) } }
    val luma = frames.getOrNull(index)?.luma
    val caption = stringResource(Res.string.style_your_video)
    return remember(caption, luma, video, player, style.blursVideo) {
        LivePicture(caption, luma) { modifier ->
            VideoSurface(
                file = viewModel.videoPath(video),
                state = player,
                clipUs = video.trimInUs..video.trimOutUs,
                modifier = modifier,
                drawFrames = style.blursVideo,
            )
        }
    }
}
