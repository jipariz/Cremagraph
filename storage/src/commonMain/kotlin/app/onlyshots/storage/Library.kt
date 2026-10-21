package app.onlyshots.storage

import app.onlyshots.core.model.VideoItem

/** The library as the app sees it: one video and its sidecar at a time, the sidecar being the truth. */
class Library(private val store: LibraryStore) {
    fun save(item: VideoItem) = store.write(item)

    fun load(id: String): VideoItem? = store.read(id)

    fun delete(items: Collection<VideoItem>) {
        for (item in items) store.delete(item)
    }
}
