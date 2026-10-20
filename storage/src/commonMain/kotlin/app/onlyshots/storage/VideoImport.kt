package app.onlyshots.storage

import okio.FileSystem
import okio.Path

/** How a video picked by the user enters the library: its id and its file name inside the library folder. */
fun interface VideoImport {
    fun import(source: Path, extension: String): Pair<String, String>

    /** A copy into the library folder, so the library never depends on where the file came from. */
    class Copy(private val store: LibraryStore, private val sourceFs: FileSystem) : VideoImport {
        override fun import(source: Path, extension: String): Pair<String, String> =
            store.importCopy(source, sourceFs = sourceFs, extension = extension)
    }
}
