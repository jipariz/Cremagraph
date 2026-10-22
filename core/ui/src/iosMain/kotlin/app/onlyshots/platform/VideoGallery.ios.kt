package app.onlyshots.platform

import androidx.compose.runtime.Composable
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_gallery_photos_refused
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Path
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Photos, through an add-only change request. A saved video is its asset's id; iOS has no public way to open Photos on
 * one asset, so [open] opens Photos itself, where the newest video is first.
 */
class IosGallery : VideoGallery {
    override suspend fun save(file: Path) {
        saveVideo(file)
    }

    override suspend fun saveVideo(file: Path): SavedVideo? {
        val refused = getString(Res.string.core_gallery_photos_refused)
        return suspendCancellableCoroutine { cont ->
            val url = NSURL.fileURLWithPath(file.toString())
            var asset: String? = null
            PHPhotoLibrary.sharedPhotoLibrary()
                .performChanges(
                    {
                        asset =
                            PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(url)
                                ?.placeholderForCreatedAsset
                                ?.localIdentifier
                    },
                    completionHandler = { ok, error ->
                        if (ok) cont.resume(asset?.let(::SavedVideo))
                        else cont.resumeWithException(IllegalStateException(error?.localizedDescription ?: refused))
                    },
                )
        }
    }

    override fun open(saved: SavedVideo): Boolean {
        val photos = NSURL.URLWithString("photos-redirect://") ?: return false
        UIApplication.sharedApplication.openURL(photos, emptyMap<Any?, Any?>(), null)
        return true
    }
}

@Composable
actual fun rememberGalleryAccess(onResult: (granted: Boolean) -> Unit): () -> Unit = {
    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
        val granted = status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
        dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
    }
}
