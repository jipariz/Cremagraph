package app.onlyshots.platform

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath

@Composable
actual fun rememberShotFilePicker(onPicked: (Path) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val copy =
                    withContext(Dispatchers.IO) {
                        // Under its own name (shown on the phones' open screen), in a folder of its own so two picks
                        // never meet.
                        val name =
                            context.contentResolver
                                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                                ?.substringAfterLast('/')
                                ?.takeIf { it.isNotBlank() } ?: "shot.json"
                        val dir = File(context.cacheDir, "shot-${System.currentTimeMillis()}").apply { mkdirs() }
                        val out = File(dir, name)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        } ?: return@withContext null
                        out
                    }
                copy?.let { onPicked(it.toOkioPath()) }
            }
        }
    // A .shot file is served as json by some exporters and as a plain download by others, so the
    // filter is the file manager's whole list rather than a type nobody agrees on.
    return { launcher.launch(arrayOf("*/*")) }
}
