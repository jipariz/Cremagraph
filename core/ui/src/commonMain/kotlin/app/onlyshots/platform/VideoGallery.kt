package app.onlyshots.platform

import androidx.compose.runtime.Composable
import okio.Path

/** The phone's own photo and video gallery: Photos on iOS, the Movies folder on Android. */
interface VideoGallery {
    /** Copies the rendered [file] into the gallery, in a OnlyShots album or folder; throws if it could not. */
    suspend fun save(file: Path)

    /** [save], and how to find the video again: null where the gallery gives nothing back (a download, a folder). */
    suspend fun saveVideo(file: Path): SavedVideo? {
        save(file)
        return null
    }

    /** Shows the [saved] video in the gallery's own app; false where that cannot be done. */
    fun open(saved: SavedVideo): Boolean = false
}

/** A video in the gallery: its content Uri on Android, its Photos asset id on iOS. */
data class SavedVideo(val ref: String)

/**
 * Returns a function that asks for whatever the gallery needs before a save, then calls [onResult]: add-only Photos
 * access on iOS, storage on Android 9 and older (newer Android needs nothing to add a video).
 */
@Composable expect fun rememberGalleryAccess(onResult: (granted: Boolean) -> Unit): () -> Unit
