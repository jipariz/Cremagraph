package app.onlyshots.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.onlyshots.app.shared.R

/**
 * The export's notifications on Android: the ongoing one with its progress (the foreground service's own, promoted to a
 * Live Update on Android 16 where the user lets it), and the one left when it ends, saved or failed. The words come
 * resolved: resources are read off the main thread, and these are built on it.
 */
object ExportNotifications {
    /** The video the app opens on, when it is started from one of these. */
    const val EXTRA_OPEN_VIDEO = "app.onlyshots.openVideo"

    internal const val PROGRESS_ID = 1
    private const val CHANNEL_PROGRESS = "exports"
    private const val CHANNEL_RESULTS = "export-results"
    private val SIGNAL = 0xFFFF5A1F.toInt()

    /** The two channels: the progress, silent, and the results, which make the usual sound. */
    fun channels(context: Context, progressName: String, resultsName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, progressName, NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, resultsName, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Whether the user lets the app post at all; without it the progress shows only in the system's task manager. */
    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** "Making your video", [text] under it, the bar at [fraction]; a tap opens the editor on [id], Cancel stops it. */
    fun progress(
        context: Context,
        id: String,
        title: String,
        text: String,
        cancel: String,
        fraction: Float,
    ): Notification {
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        val builder =
            NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setColor(SIGNAL)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp(context, id))
                .addAction(0, cancel, cancelIntent(context))
                .setProgress(100, percent, false)
                // Android 16: the status bar's chip and the card on top of the lock screen, where the user allows it.
                .setRequestPromotedOngoing(true)
                .setShortCriticalText("$percent%")
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .setStyledByProgress(false)
                    .addProgressSegment(NotificationCompat.ProgressStyle.Segment(100).setColor(SIGNAL))
                    .setProgress(percent)
            )
        }
        return builder.build()
    }

    /** In the gallery: a tap plays it there; Share sends it; the video's frame where the gallery gives one. */
    fun saved(
        context: Context,
        id: String,
        saved: SavedVideo?,
        title: String,
        text: String,
        open: String,
        share: String,
    ): Notification {
        val view = saved?.let { activity(context, id.hashCode(), viewIntent(it)) } ?: openApp(context, id)
        return NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(SIGNAL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(view)
            .apply { saved?.let { thumbnail(context, it) }?.let(::setLargeIcon) }
            .addAction(0, open, view)
            .apply {
                saved?.let {
                    addAction(0, share, activity(context, id.hashCode() + 1, shareIntent(it, null)))
                }
            }
            .build()
    }

    /** Not made: what went wrong, and Try again, which opens the editor on it. */
    fun failed(context: Context, id: String, title: String, text: String, retry: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(SIGNAL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, id))
            .addAction(0, retry, openApp(context, id))
            .build()

    /** One result per video, so a second export of it replaces the first one's. */
    internal fun resultId(id: String): Int = 1_000 + (id.hashCode() and 0xFFFF)

    private fun thumbnail(context: Context, saved: SavedVideo): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.loadThumbnail(Uri.parse(saved.ref), Size(256, 256), null) }
                .getOrNull()
        } else null

    private fun openApp(context: Context, id: String): PendingIntent {
        val intent =
            (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                .putExtra(EXTRA_OPEN_VIDEO, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return activity(context, id.hashCode() + 2, intent)
    }

    private fun activity(context: Context, request: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            request,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun cancelIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ExportCancelReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Cancel, from the progress notification: stops the render the way the export screen's Cancel does. */
class ExportCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ExportControl.cancel?.invoke()
    }
}

/** How the notification reaches the render it shows: set while one runs. */
internal object ExportControl {
    @Volatile var cancel: (() -> Unit)? = null
}
