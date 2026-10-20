package app.onlyshots.style

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.resolve
import app.onlyshots.core.resources.style_copy_name
import app.onlyshots.core.resources.style_my_style
import app.onlyshots.core.resources.text
import app.onlyshots.storage.LibraryStore
import app.onlyshots.video.VideoProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * One style being edited: a user style changed in place ([editId]), or a new one starting from [baseId] (a built-in or
 * a user style; built-ins are never edited in place). The preview runs on frames of [videoId], else of the newest video
 * in the library, else on the drawn espresso scene.
 */
class StyleEditorViewModel(
    baseId: String?,
    editId: String?,
    private val videoId: String?,
    private val styles: StyleLibrary,
    private val store: LibraryStore,
    private val probe: VideoProbe,
    private val fs: FileSystem,
    private val cacheDir: Path,
) : ViewModel() {
    private val editing: GraphStyle? = editId?.let { id -> styles.custom.firstOrNull { it.id == id } }

    /** True for a style that is not saved yet: saving adds it rather than replacing one. */
    val isNew: Boolean = editing == null

    private val base: GraphStyle? = if (editing == null) baseId?.let(styles::byId) else null

    // A new style's name is put into words below, as it is saved with the style; until then, its base's name.
    private val original: GraphStyle =
        editing ?: styles.copyOf(base ?: styles.resolve(OverlaySettings(), null), name = base?.name.orEmpty())

    val draft: StateFlow<GraphStyle>
        field = MutableStateFlow(original)

    /** The video the editor was opened from ([videoId]), playing under the preview; null until read, or without one. */
    val video: StateFlow<VideoItem?>
        field = MutableStateFlow(null)

    /** Frames of the user's video to preview on; empty until loaded, or for good when there is no video. */
    val frames: StateFlow<List<ImageBitmap>>
        field = MutableStateFlow(emptyList())

    /** Unsaved changes, or a new style not saved yet: leaving asks first. */
    val dirty: Boolean
        get() = isNew || draft.value != original

    init {
        if (isNew) {
            viewModelScope.launch {
                val name = newStyleName(base).resolve()
                draft.update { if (it.name == original.name) it.copy(name = name) else it }
            }
        }
        viewModelScope.launch { loadFrames() }
    }

    fun videoPath(item: VideoItem): Path = store.videoPath(item)

    /** The stored frame nearest [positionUs] of [item]: its brightness stands in for the playing video's there. */
    fun frameAt(item: VideoItem, positionUs: Long): Int {
        val span = (item.trimOutUs - item.trimInUs).coerceAtLeast(1)
        return ((positionUs - item.trimInUs).toDouble() / span * FRAMES).toInt().coerceIn(0, FRAMES - 1)
    }

    fun change(change: (GraphStyle) -> GraphStyle) {
        draft.value = change(draft.value)
    }

    /** The sources this style is the default for now. */
    fun defaults(): Set<SourceKind> = if (isNew) emptySet() else styles.defaultsOf(original.id)

    /** Saves under [name], as the default for [defaultFor]; returns the style's id. */
    fun save(name: String, defaultFor: Set<SourceKind>): String {
        val style = draft.value.copy(name = name.trim().ifEmpty { draft.value.name }, builtIn = false)
        styles.save(style, defaultFor)
        draft.value = style
        return style.id
    }

    private suspend fun loadFrames() {
        val video: VideoItem =
            withContext(Dispatchers.Default) {
                videoId?.let { store.read(it) }
                    ?: runCatching { store.scan().videos.maxByOrNull { it.item.createdAt }?.item }.getOrNull()
            } ?: return
        if (video.id == videoId) this.video.value = video
        val dir = cacheDir / "style-frames" / video.id
        val list = ArrayList<ImageBitmap>(FRAMES)
        for (i in 0 until FRAMES) {
            // Frames from the middle of the clip: the shot, not the idle head or the empty tail.
            val atUs = video.trimInUs + ((i + 0.5) / FRAMES * (video.trimOutUs - video.trimInUs)).toLong()
            val bitmap =
                withContext(Dispatchers.Default) {
                    runCatching {
                        fs.createDirectories(dir)
                        val out = dir / "$i.jpg"
                        if (!fs.exists(out))
                            probe.writeThumbnail(store.videoPath(video), atUs, out, maxWidthPx = WIDTH_PX)
                        fs.read(out) { readByteArray() }.decodeToImageBitmap()
                    }
                        .getOrNull()
                } ?: return
            list += bitmap
            frames.value = list.toList()
        }
    }

    private companion object {
        const val FRAMES = 12
        const val WIDTH_PX = 720
    }
}

/** The name a new style starts with: [base]'s with "copy", or "my style" when it starts from the default. */
internal fun newStyleName(base: GraphStyle?): UiText =
    base?.let { Res.string.style_copy_name.text(it.name) } ?: Res.string.style_my_style.text()
