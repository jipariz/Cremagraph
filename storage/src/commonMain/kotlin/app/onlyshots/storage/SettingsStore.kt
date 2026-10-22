package app.onlyshots.storage

import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.style.GraphStyle
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

@Serializable
data class AppSettings(
    val firstRunDone: Boolean = false,
    val source: SourceKind? = null,
    /** User-entered or found host or IP (with an optional port); null means the source's default `.local` name. */
    val host: String? = null,
    val scaleId: String? = null,
    /** The scale's advertised name, so Home can name it before the scale is found again. */
    val scaleName: String? = null,
    val recording: RecordingSettings = RecordingSettings(),
    /** Graph styles the user built in the style editor, in the order they were made. */
    val customStyles: List<GraphStyle> = emptyList(),
    /** A user style chosen as a source's default: overrides the built-in default for that source. */
    val defaultStyles: Map<SourceKind, String> = emptyMap(),
    /** The export quality last picked for saving to the gallery; null: the best the video allows. */
    val galleryQuality: Quality? = null,
    /** The export quality last picked for sharing; null: 1080p. */
    val shareQuality: Quality? = null,
    /** The export screen has asked (once) whether to tell the user when an export is done. */
    val exportNotificationsAsked: Boolean = false,
)

/** App settings in one JSON file; unreadable files fall back to defaults. */
class SettingsStore(private val fs: FileSystem, private val path: Path) {
    fun load(): AppSettings =
        try {
            if (!fs.exists(path)) AppSettings()
            else SidecarCodec.json.decodeFromString(AppSettings.serializer(), fs.read(path) { readUtf8() })
        } catch (e: Exception) {
            AppSettings()
        }

    fun save(settings: AppSettings) {
        path.parent?.let { fs.createDirectories(it) }
        val tmp = path.parent!! / "${path.name}.tmp"
        fs.sink(tmp).buffer().use { it.writeUtf8(SidecarCodec.json.encodeToString(AppSettings.serializer(), settings)) }
        fs.atomicMove(tmp, path)
    }
}
