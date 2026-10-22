package app.onlyshots.platform

import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_gallery_refused
import app.onlyshots.core.resources.core_gallery_write_failed
import org.jetbrains.compose.resources.getString
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
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

/**
 * Movies/OnlyShots, through MediaStore on Android 10 and later, as a plain file plus a media scan before that. A saved
 * video is its MediaStore Uri: the gallery's player opens it, the share sheet sends it.
 */
class AndroidGallery(private val context: Context) : VideoGallery {
    override suspend fun save(file: Path) {
        saveVideo(file)
    }

    override fun open(saved: SavedVideo): Boolean = start(viewIntent(saved))

    override fun canShare(saved: SavedVideo): Boolean = true

    override fun share(saved: SavedVideo): Boolean = start(shareIntent(saved, null))

    private fun start(intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }

    override suspend fun saveVideo(file: Path): SavedVideo? {
        val refused = getString(Res.string.core_gallery_refused)
        val writeFailed = getString(Res.string.core_gallery_write_failed)
        return withContext(Dispatchers.IO) {
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
                SavedVideo(uri.toString())
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "OnlyShots")
                dir.mkdirs()
                val out = File(dir, name)
                File(file.toString()).copyTo(out)
                MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf("video/mp4"), null)
                // Before Android 10 the file is in Movies itself; the scan gives its Uri later, so the path stands in.
                SavedVideo(Uri.fromFile(out).toString())
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

/** The gallery's player on the [saved] video, allowed to read it. */
fun viewIntent(saved: SavedVideo): Intent =
    Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(saved.ref), "video/mp4")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

/** The share sheet for the [saved] video; [title] heads it. */
fun shareIntent(saved: SavedVideo, title: String?): Intent =
    Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse(saved.ref))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            title,
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
