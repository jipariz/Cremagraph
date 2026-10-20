package app.onlyshots.platform

import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_gallery_refused
import app.onlyshots.core.resources.core_gallery_write_failed
import org.jetbrains.compose.resources.getString
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path

/** Movies/OnlyShots, through MediaStore on Android 10 and later, as a plain file plus a media scan before that. */
class AndroidGallery(private val context: Context) : VideoGallery {
    override suspend fun save(file: Path) {
        val refused = getString(Res.string.core_gallery_refused)
        val writeFailed = getString(Res.string.core_gallery_write_failed)
        withContext(Dispatchers.IO) {
            val name = "onlyshots-${System.currentTimeMillis()}.mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values =
                    ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/OnlyShots")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                val uri =
                    resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                        ?: error(refused)
                try {
                    resolver.openOutputStream(uri)?.use { out -> File(file.toString()).inputStream().use { it.copyTo(out) } }
                        ?: error(writeFailed)
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "OnlyShots")
                dir.mkdirs()
                val out = File(dir, name)
                File(file.toString()).copyTo(out)
                MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf("video/mp4"), null)
            }
        }
    }
}

@Composable
actual fun rememberGalleryAccess(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        val needs =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
        if (needs) launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else onResult(true)
    }
}
