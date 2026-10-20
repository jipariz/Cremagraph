package app.onlyshots.storage

import app.onlyshots.core.model.VideoItem
import okio.FileSystem

/**
 * The library as the app sees it: writes go to the sidecar first, then the index. The index is rebuilt from the
 * sidecars at start-up with [reconcile].
 */
class Library(private val store: LibraryStore, private val index: LibraryIndex, private val fs: FileSystem) {
    fun save(item: VideoItem) {
        store.write(item)
        index.upsert(item, fs.metadata(store.sidecarPath(item.id)).lastModifiedAtMillis ?: 0L)
    }

    fun load(id: String): VideoItem? = store.read(id)

    fun delete(items: Collection<VideoItem>) {
        for (item in items) {
            store.delete(item)
            index.delete(item.id)
        }
    }

    fun reconcile(): ScanResult = store.scan().also { index.reconcile(it) }
}
