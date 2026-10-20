package app.onlyshots.open

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.onlyshots.core.model.GaggiuinoShotFile
import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.open_error_not_a_shot
import app.onlyshots.core.resources.open_error_unreadable
import app.onlyshots.core.resources.open_error_video
import app.onlyshots.core.resources.open_status_opening
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.text
import app.onlyshots.storage.Library
import app.onlyshots.storage.LibraryStore
import app.onlyshots.storage.VideoImport
import app.onlyshots.video.VideoInfo
import app.onlyshots.video.VideoProbe
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Every editor's start: the video (copied into the library as it is picked) and its shot (a file read now, or a
 * machine's shot handed in), then "open editor" saves the shot onto the video.
 */
class OpenViewModel(
    private val store: LibraryStore,
    private val library: Library,
    private val probe: VideoProbe,
    private val fs: FileSystem,
    private val intake: VideoImport,
) : ViewModel() {
    /** "opening…" while a video is copied in, or why it could not be. */
    val status: StateFlow<UiText?>
        field = MutableStateFlow<UiText?>(null)

    /** The picked video's own file name and size, for the start screen to say what was picked. */
    val pickedFile: StateFlow<PickedFile?>
        field = MutableStateFlow<PickedFile?>(null)

    /** A frame of the picked video, for its tile; null until it is decoded, or when it cannot be. */
    val pickedThumb: StateFlow<ImageBitmap?>
        field = MutableStateFlow<ImageBitmap?>(null)

    /** The video picked, already in the library, waiting for its shot and "open editor". */
    val picked: StateFlow<VideoItem?>
        field = MutableStateFlow<VideoItem?>(null)

    /** The shot picked for it. */
    val shot: StateFlow<ChosenShot?>
        field = MutableStateFlow<ChosenShot?>(null)

    /** A `.shot` or `.json` file, read now so a file that is no shot is said here, not later in the editor. */
    fun chooseFile(path: Path) {
        viewModelScope.launch {
            val parsed =
                withContext(Dispatchers.Default) {
                    runCatching { GaggiuinoShotFile.parse(fs.read(path) { readUtf8() }, fallbackId = path.name) }
                        .getOrNull()
                }
            if (parsed == null) status.value = Res.string.open_error_not_a_shot.text(path.name)
            else {
                shot.value = ChosenShot(parsed, path.name, "file")
                status.value = null
            }
        }
    }

    /** A shot from elsewhere: the machine shot picker's. */
    fun chooseShot(picked: Shot, label: String, from: String) {
        shot.value = ChosenShot(picked, label, from)
        status.value = null
    }

    /** Copies [file] into the library and keeps it as [picked]. */
    fun pickVideo(file: Path) {
        if (status.value == Opening) return
        status.value = Opening
        viewModelScope.launch {
            val item = withContext(Dispatchers.Default) { runCatching { import(file) } }
            item.fold(
                { (video, info) ->
                    status.value = null
                    picked.value = video
                    pickedFile.value = PickedFile(file.name, info.widthPx, info.heightPx)
                    pickedThumb.value = null
                    thumbnail(video)
                },
                {
                    status.value =
                        Res.string.open_error_video.text(
                            file.name,
                            it.message?.raw() ?: Res.string.open_error_unreadable.text(),
                        )
                },
            )
        }
    }

    /**
     * "open editor": the shot is saved onto the picked video, not lined up yet (the editor does that, from the pump
     * sound or the clocks), and the video's id goes to [onOpened]. The next visit starts from nothing picked.
     */
    fun openWith(onOpened: (String) -> Unit) {
        val item = picked.value ?: return
        val chosen = shot.value ?: return
        viewModelScope.launch {
            val withShot = item.copy(shot = chosen.shot, shotStartInVideoUs = null)
            withContext(Dispatchers.Default) { library.save(withShot) }
            picked.value = null
            pickedFile.value = null
            pickedThumb.value = null
            shot.value = null
            onOpened(item.id)
        }
    }

    /**
     * A frame from about 40 % in (the start is often the machine idling, the end an empty cup) for the picked tile,
     * written next to the library's other thumbnails.
     */
    private fun thumbnail(video: VideoItem) {
        viewModelScope.launch {
            val bitmap =
                withContext(Dispatchers.Default) {
                    runCatching {
                        val out = store.thumbnailPath(video.id)
                        probe.writeThumbnail(
                            store.videoPath(video),
                            atUs = (video.durationUs * 0.4).toLong(),
                            out = out,
                            maxWidthPx = THUMB_WIDTH_PX,
                        )
                        fs.read(out) { readByteArray() }.decodeToImageBitmap()
                    }
                        .getOrNull()
                }
            // A newer pick may have come in meanwhile: only that one's frame belongs in the tile.
            if (picked.value?.id == video.id) pickedThumb.value = bitmap
        }
    }

    private suspend fun import(file: Path): Pair<VideoItem, VideoInfo> {
        val extension = file.name.substringAfterLast('.', "mp4").lowercase()
        val (id, name) = intake.import(file, extension)
        // A copy that cannot be read leaves no file behind: there is no sidecar for reconcile to find it by.
        return try {
            val info = probe.info(store.videosDir / name)
            VideoItem(
                    id = id,
                    file = name,
                    createdAt = info.recordedAt ?: Clock.System.now(),
                    durationUs = info.durationUs,
                    origin = Origin.IMPORTED,
                    hasAudio = info.hasAudio,
                    shot = null,
                    shotStartInVideoUs = null,
                    syncLocked = false,
                    trimInUs = 0,
                    trimOutUs = info.durationUs,
                    overlay = OverlaySettings(),
                    recordedAt = info.recordedAt,
                )
                .also(library::save) to info
        } catch (e: Throwable) {
            fs.delete(store.videosDir / name, mustExist = false)
            throw e
        }
    }

    private companion object {
        /** Twice the tile's widest drawing (54 dp at 2–3×): sharp, and small to decode. */
        const val THUMB_WIDTH_PX = 180
    }
}

/** "opening…": the status while a picked video is copied in, which the steps show in the video's place. */
internal val Opening: UiText = Res.string.open_status_opening.text()

/**
 * The picked video as its file was named, its size in pixels as shown (0 when unknown), and a frame of it for the tile
 * once one is decoded.
 */
data class PickedFile(val name: String, val widthPx: Int, val heightPx: Int, val thumb: ImageBitmap? = null)
