@file:OptIn(ExperimentalForeignApi::class)

package app.onlyshots.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

@Composable
internal actual fun rememberPlatformShotFilePicker(onPicked: (Path) -> Unit): () -> Unit {
    // The delegate has to outlive the call that presents the picker.
    val delegate = remember { ShotFileDelegate() }
    delegate.onPicked = onPicked
    return {
        // JSON first so the picker highlights those; UTTypeData lets a .shot with no declared
        // type through, which is how SproFiler's download arrives.
        val picker =
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypeData), asCopy = true)
                .apply { setDelegate(delegate) }
        UIApplication.sharedApplication.keyWindow
            ?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
    }
}

private class ShotFileDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onPicked: (Path) -> Unit = {}

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val path = url.path ?: return
        // Under its own name (shown on the open screen), in a folder of its own so two picks never meet.
        val dir = NSTemporaryDirectory() + "shot-${NSUUID().UUIDString}"
        val files = NSFileManager.defaultManager
        if (!files.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null))
            return
        val copy = "$dir/" + (url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "shot.json")
        if (files.copyItemAtPath(path, copy, null)) onPicked(copy.toPath())
    }
}
