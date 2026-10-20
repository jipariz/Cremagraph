package app.onlyshots.storage

import app.onlyshots.core.model.Sidecar
import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.core.model.VideoItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** A sidecar on disk and when it was last written. */
data class StoredVideo(val item: VideoItem, val sidecarModifiedMs: Long)

/** A sidecar that could not be read; kept on disk untouched. */
data class UnreadableSidecar(val path: Path, val reason: String)

data class ScanResult(val videos: List<StoredVideo>, val unreadable: List<UnreadableSidecar>)

/**
 * The library on disk: `videos/<id>.<ext>` plus `videos/<id>.onlyshots.json`, and thumbnails in `thumbs/`. Videos live
 * only here, never in Photos. The sidecar is the source of truth.
 */
class LibraryStore(private val fs: FileSystem, root: Path) {
    val videosDir: Path = root / "videos"
    val thumbsDir: Path = root / "thumbs"

    init {
        fs.createDirectories(videosDir)
        fs.createDirectories(thumbsDir)
        renameLegacySidecars()
    }

    /** Sidecars written before the rename from Cremagraph get today's name, so the rest of the store sees only that. */
    private fun renameLegacySidecars() {
        for (path in fs.list(videosDir)) {
            if (!path.name.endsWith(Sidecar.LEGACY_SUFFIX)) continue
            val target = videosDir / Sidecar.fileName(path.name.removeSuffix(Sidecar.LEGACY_SUFFIX))
            if (fs.exists(target)) fs.delete(path) else fs.atomicMove(path, target)
        }
    }

    fun videoPath(item: VideoItem): Path = videosDir / item.file

    fun sidecarPath(id: String): Path = videosDir / Sidecar.fileName(id)

    fun thumbnailPath(id: String): Path = thumbsDir / "$id.jpg"

    @OptIn(ExperimentalUuidApi::class) fun newId(): String = Uuid.random().toHexString()

    /** Writes the sidecar atomically (temp file, then move), so a crash never leaves half a file. */
    fun write(item: VideoItem) {
        val target = sidecarPath(item.id)
        val tmp = videosDir / "${item.id}.onlyshots.json.tmp"
        fs.sink(tmp).buffer().use { it.writeUtf8(SidecarCodec.encode(item)) }
        fs.atomicMove(tmp, target)
    }

    fun read(id: String): VideoItem? {
        val path = sidecarPath(id)
        if (!fs.exists(path)) return null
        return SidecarCodec.decode(fs.read(path) { readUtf8() })
    }

    fun scan(): ScanResult {
        val videos = ArrayList<StoredVideo>()
        val unreadable = ArrayList<UnreadableSidecar>()
        for (path in fs.list(videosDir)) {
            if (!path.name.endsWith(Sidecar.SUFFIX)) continue
            try {
                val item = SidecarCodec.decode(fs.read(path) { readUtf8() })
                val modified = fs.metadata(path).lastModifiedAtMillis ?: 0L
                videos += StoredVideo(item, modified)
            } catch (e: Exception) {
                unreadable += UnreadableSidecar(path, e.message ?: e::class.simpleName.orEmpty())
            }
        }
        return ScanResult(videos, unreadable)
    }

    /** Copies an imported file into the library and returns its new id and file name. */
    fun importCopy(source: Path, sourceFs: FileSystem = fs, extension: String = "mp4"): Pair<String, String> {
        val id = newId()
        val file = "$id.$extension"
        sourceFs.source(source).use { input -> fs.sink(videosDir / file).buffer().use { it.writeAll(input) } }
        return id to file
    }

    /**
     * Moves a finished recording into the library. The camera writes into the cache directory, which is on the same
     * volume, so this is a rename, not a copy of a 4K file.
     */
    fun importMove(source: Path, extension: String = "mp4"): Pair<String, String> {
        val id = newId()
        val file = "$id.$extension"
        fs.atomicMove(source, videosDir / file)
        return id to file
    }

    /** Deletes the video, its sidecar and thumbnail right away so the space is freed immediately. */
    fun delete(item: VideoItem) {
        fs.delete(videoPath(item), mustExist = false)
        fs.delete(sidecarPath(item.id), mustExist = false)
        fs.delete(thumbnailPath(item.id), mustExist = false)
    }
}
