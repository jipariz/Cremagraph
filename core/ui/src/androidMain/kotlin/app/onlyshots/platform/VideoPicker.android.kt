package app.onlyshots.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath

@Composable
actual fun rememberVideoPicker(onPicked: (Path) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // OpenDocument reaches both Photos (through the media provider) and Files.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copy = withContext(Dispatchers.IO) {
                val out = File(context.cacheDir, "import-${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: return@withContext null
                out
            }
            copy?.let { onPicked(it.toOkioPath()) }
        }
    }
    return { launcher.launch(arrayOf("video/*")) }
}

actual fun Modifier.videoDrop(onFile: (Path) -> Unit, onHover: (Boolean) -> Unit): Modifier = this
