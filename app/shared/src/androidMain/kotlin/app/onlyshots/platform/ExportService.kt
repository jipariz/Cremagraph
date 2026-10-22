package app.onlyshots.platform

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * The foreground service that holds the process up while the export renders, its notification the export's progress
 * ([ExportNotifications.progress]). [ForegroundWork] starts and stops it, and updates the notification as it goes.
 */
class ExportService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The words come resolved from [ForegroundWork]: resources are read off the main thread, this runs on it.
        val notification =
            ExportNotifications.progress(
                this,
                id = intent?.getStringExtra(EXTRA_ID).orEmpty(),
                title = intent?.getStringExtra(EXTRA_TITLE).orEmpty(),
                text = intent?.getStringExtra(EXTRA_TEXT).orEmpty(),
                cancel = intent?.getStringExtra(EXTRA_CANCEL).orEmpty(),
                fraction = 0f,
            )
        // mediaProcessing exists from Android 15; Android 14 throws on it as an unknown type, so 10–14 use dataSync.
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                startForeground(
                    ExportNotifications.PROGRESS_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                startForeground(ExportNotifications.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> startForeground(ExportNotifications.PROGRESS_ID, notification)
        }
        return START_NOT_STICKY
    }

    /** Android 15's limit on media processing (6 hours a day): stop the render, then the service, in time. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ExportControl.cancel?.invoke()
        stopSelf()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_CANCEL = "cancel"
    }
}
