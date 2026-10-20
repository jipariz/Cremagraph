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
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Photos, through an add-only change request. */
class IosGallery : VideoGallery {
    override suspend fun save(file: Path) {
        val refused = getString(Res.string.core_gallery_photos_refused)
        suspendCancellableCoroutine { cont ->
            val url = NSURL.fileURLWithPath(file.toString())
            PHPhotoLibrary.sharedPhotoLibrary()
                .performChanges(
                    { PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(url) },
                    completionHandler = { ok, error ->
                        if (ok) cont.resume(Unit)
                        else cont.resumeWithException(IllegalStateException(error?.localizedDescription ?: refused))
                    },
                )
        }
    }
}

@Composable
actual fun rememberGalleryAccess(onResult: (granted: Boolean) -> Unit): () -> Unit = {
    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
        val granted = status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
        dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
    }
}
