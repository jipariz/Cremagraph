package app.onlyshots.storage

import okio.FileSystem
import okio.Path

/**
 * Rendered exports waiting for the share sheet. Each file is removed after the share completes or, at the latest, 24
 * hours after it was made.
 */
class ShareTempFiles(private val fs: FileSystem, private val dir: Path) {
    init {
        fs.createDirectories(dir)
    }

    fun newFile(videoId: String, nowMs: Long): Path = dir / "onlyshots-$videoId-$nowMs.mp4"

    fun release(path: Path) = fs.delete(path, mustExist = false)

    /** Deletes files older than [maxAgeMs]; returns how many were removed. */
    fun cleanup(nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Int {
        var removed = 0
        for (p in fs.list(dir)) {
            val modified = fs.metadata(p).lastModifiedAtMillis ?: continue
            if (nowMs - modified >= maxAgeMs) {
                fs.delete(p, mustExist = false)
                removed++
            }
        }
        return removed
    }

    companion object {
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
