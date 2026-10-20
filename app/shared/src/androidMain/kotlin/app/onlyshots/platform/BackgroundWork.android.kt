package app.onlyshots.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_export_notification_channel
import app.onlyshots.core.resources.core_export_notification_title
import org.jetbrains.compose.resources.getString

/** Runs the block with [ExportService] in the foreground, so Android keeps the process alive. */
class ForegroundWork(private val context: Context) : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T {
        val intent =
            Intent(context, ExportService::class.java)
                .putExtra(
                    ExportService.EXTRA_TITLE,
                    title.ifBlank { getString(Res.string.core_export_notification_title) },
                )
                .putExtra(ExportService.EXTRA_CHANNEL_NAME, getString(Res.string.core_export_notification_channel))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        return try {
            block()
        } finally {
            context.stopService(Intent(context, ExportService::class.java))
        }
    }
}
