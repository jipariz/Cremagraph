@file:OptIn(ExperimentalForeignApi::class)

package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_pick_cancel
import app.onlyshots.core.resources.core_pick_from_files
import app.onlyshots.core.resources.core_pick_from_photos
import app.onlyshots.core.resources.core_pick_video_title
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberVideoPicker(onPicked: (Path) -> Unit): () -> Unit {
    // Both delegates have to outlive the call that presents their picker.
    val photos = remember { PickerDelegate() }
    val files = remember { DocumentDelegate() }
    photos.onPicked = onPicked
    files.onPicked = onPicked
    val title = stringResource(Res.string.core_pick_video_title)
    val fromPhotos = stringResource(Res.string.core_pick_from_photos)
    val fromFiles = stringResource(Res.string.core_pick_from_files)
    val cancel = stringResource(Res.string.core_pick_cancel)
    return {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        val sheet =
            UIAlertController.alertControllerWithTitle(
                title = title,
                message = null,
                preferredStyle = UIAlertControllerStyleActionSheet,
            )
        sheet.addAction(
            UIAlertAction.actionWithTitle(fromPhotos, UIAlertActionStyleDefault) {
                root?.presentViewController(photoPicker(photos), animated = true, completion = null)
            }
        )
        sheet.addAction(
            UIAlertAction.actionWithTitle(fromFiles, UIAlertActionStyleDefault) {
                root?.presentViewController(documentPicker(files), animated = true, completion = null)
            }
        )
        sheet.addAction(UIAlertAction.actionWithTitle(cancel, UIAlertActionStyleCancel, null))
        // An action sheet with no anchor crashes on iPad, and this app runs windowed there.
        root?.view?.let { view ->
            sheet.popoverPresentationController?.sourceView = view
            sheet.popoverPresentationController?.sourceRect = view.bounds
        }
        root?.presentViewController(sheet, animated = true, completion = null)
    }
}

private fun photoPicker(delegate: PickerDelegate): UIViewController {
    val config =
        PHPickerConfiguration().apply {
            filter = PHPickerFilter.videosFilter
            selectionLimit = 1
        }
    return PHPickerViewController(config).apply { setDelegate(delegate) }
}

private fun documentPicker(delegate: DocumentDelegate): UIViewController =
    UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeMovie), asCopy = true).apply {
        setDelegate(delegate)
    }

/** The system file is deleted as soon as the callback returns, so copy it first. */
private fun copyIntoTemp(source: NSURL): Path? {
    val path = source.path ?: return null
    val copy = NSTemporaryDirectory() + "import-${NSUUID().UUIDString}.mp4"
    return if (NSFileManager.defaultManager.copyItemAtPath(path, copy, null)) copy.toPath() else null
}

private class PickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {
    var onPicked: (Path) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadFileRepresentationForTypeIdentifier(UTTypeMovie.identifier) { url, _ ->
            val copy = (url as? NSURL)?.let(::copyIntoTemp) ?: return@loadFileRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onPicked(copy) }
        }
    }
}

/** Files, for a video that never went through Photos: a share from another app, or iCloud Drive. */
private class DocumentDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onPicked: (Path) -> Unit = {}

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        // asCopy = true already puts the file in the app's own tmp, but it is named by the user and
        // reused on the next import; the library's own copy keeps the name it chose.
        copyIntoTemp(url)?.let(onPicked)
    }
}

actual fun Modifier.videoDrop(onFile: (Path) -> Unit, onHover: (Boolean) -> Unit): Modifier = this
