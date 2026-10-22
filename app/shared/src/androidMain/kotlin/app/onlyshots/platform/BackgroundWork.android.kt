package app.onlyshots.platform

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_export_making
import app.onlyshots.core.resources.core_export_notification_channel
import app.onlyshots.core.resources.core_export_notification_title
import app.onlyshots.core.resources.core_export_notify_cancel
import app.onlyshots.core.resources.core_export_notify_failed
import app.onlyshots.core.resources.core_export_notify_failed_text
import app.onlyshots.core.resources.core_export_notify_open
import app.onlyshots.core.resources.core_export_notify_progress
import app.onlyshots.core.resources.core_export_notify_progress_early
import app.onlyshots.core.resources.core_export_notify_results
import app.onlyshots.core.resources.core_export_notify_retry
import app.onlyshots.core.resources.core_export_notify_saved
import app.onlyshots.core.resources.core_export_notify_saved_text
import app.onlyshots.core.resources.core_export_notify_share
import org.jetbrains.compose.resources.getString

/**
 * Runs the render with [ExportService] in the foreground, so Android keeps the process alive, its notification the
 * render's progress; when it ends, a notification says how, unless the app is in front and says so itself.
 */
class ForegroundWork(private val context: Context) : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T =
        keepAlive(ExportTask(id = "", name = title, quality = "", onCancel = {})) { block() }

    override suspend fun <T> keepAlive(task: ExportTask, block: suspend (report: (ExportStatus) -> Unit) -> T): T {
        ExportNotifications.channels(
            context,
            getString(Res.string.core_export_notification_channel),
            getString(Res.string.core_export_notify_results),
        )
        val title = if (task.quality.isEmpty()) task.name.ifBlank { getString(Res.string.core_export_notification_title) }
        else getString(Res.string.core_export_making)
        val early = getString(Res.string.core_export_notify_progress_early, task.name, task.quality)
        // The line with the time left, its one changing part kept as a marker until each update fills it in.
        val withLeft = getString(Res.string.core_export_notify_progress, task.name, task.quality, LEFT)
        val cancel = getString(Res.string.core_export_notify_cancel)
        val text = if (task.quality.isEmpty()) "" else early
        val intent =
            Intent(context, ExportService::class.java)
                .putExtra(ExportService.EXTRA_ID, task.id)
                .putExtra(ExportService.EXTRA_TITLE, title)
                .putExtra(ExportService.EXTRA_TEXT, text)
                .putExtra(ExportService.EXTRA_CANCEL, cancel)
        ExportControl.cancel = task.onCancel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        return try {
            block { status ->
                if (!ExportNotifications.canPost(context)) return@block
                val line = status.left?.let { withLeft.replace(LEFT, it) } ?: early
                runCatching {
                    NotificationManagerCompat.from(context)
                        .notify(
                            ExportNotifications.PROGRESS_ID,
                            ExportNotifications.progress(context, task.id, title, line, cancel, status.fraction),
                        )
                }
            }
        } finally {
            ExportControl.cancel = null
            context.stopService(Intent(context, ExportService::class.java))
        }
    }

    override suspend fun finished(end: ExportEnd) {
        if (inFront() || !ExportNotifications.canPost(context)) return
        val notification =
            when (end) {
                is ExportEnd.Saved ->
                    ExportNotifications.saved(
                        context,
                        end.id,
                        end.saved,
                        title = getString(Res.string.core_export_notify_saved),
                        text = getString(Res.string.core_export_notify_saved_text, end.name, end.length),
                        open = getString(Res.string.core_export_notify_open),
                        share = getString(Res.string.core_export_notify_share),
                    )
                is ExportEnd.Failed ->
                    ExportNotifications.failed(
                        context,
                        end.id,
                        title = getString(Res.string.core_export_notify_failed),
                        text = getString(Res.string.core_export_notify_failed_text, end.name, end.reason),
                        retry = getString(Res.string.core_export_notify_retry),
                    )
                // Android keeps a render going for hours: it is never paused.
                is ExportEnd.Paused -> return
            }
        runCatching { NotificationManagerCompat.from(context).notify(ExportNotifications.resultId(end.id), notification) }
    }

    /** Whether one of the app's screens is in front: then the export screen says how it went. */
    private fun inFront(): Boolean {
        val me = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(me)
        return me.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private companion object {
        const val LEFT = "\u0000left\u0000"
    }
}
