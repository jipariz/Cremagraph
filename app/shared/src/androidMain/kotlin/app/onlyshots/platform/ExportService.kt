package app.onlyshots.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Notification-only foreground service: it holds the process up while the export renders. */
class ExportService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Both words come resolved from [ForegroundWork]: resources are read off the main thread, this runs on it.
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: CHANNEL
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, channelName, NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setSmallIcon(app.onlyshots.app.shared.R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .build()
        // mediaProcessing exists from Android 15; Android 14 throws on it as an unknown type, so 10–14 use dataSync.
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> startForeground(ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL_NAME = "channelName"
        private const val CHANNEL = "exports"
        private const val ID = 1
    }
}
