package app.onlyshots.platform

import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_export_making
import app.onlyshots.core.resources.core_export_notify_failed
import app.onlyshots.core.resources.core_export_notify_failed_text
import app.onlyshots.core.resources.core_export_notify_paused
import app.onlyshots.core.resources.core_export_notify_paused_text
import app.onlyshots.core.resources.core_export_notify_progress
import app.onlyshots.core.resources.core_export_notify_progress_early
import app.onlyshots.core.resources.core_export_notify_saved_photos
import app.onlyshots.core.resources.core_export_notify_saved_text
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS 26's continued processing: an export the user started goes on in the background, with the system's own progress
 * on the lock screen and in the Dynamic Island. Swift's (app/iosApp), which can check for iOS 26 and Kotlin cannot
 * without linking the class on every iOS; it is [IosBackground.continued] where the system has it.
 */
interface ContinuedProcessing {
    /** Starts the system's progress for [id]; false when it cannot start now (then the short background time). */
    fun start(id: String, title: String, subtitle: String, onExpired: () -> Unit): Boolean

    /** How far it is, and the line under the title. */
    fun progress(fraction: Double, subtitle: String)

    /** Over: the system's progress goes. */
    fun finish(success: Boolean)
}

/** Where Swift hands its parts over at launch. */
object IosBackground {
    var continued: ContinuedProcessing? = null
}

/**
 * Keeps an export going while the app is in the background. On iOS 26 the system's continued processing shows its
 * progress and lets it run to the end; before, iOS grants a short while, and an export still running when it is up is
 * stopped and says so. When it ends a banner says how, unless the app is in front.
 */
class BackgroundTaskWork : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T = shortWhile(title, {}) { block() }

    override suspend fun <T> keepAlive(task: ExportTask, block: suspend (report: (ExportStatus) -> Unit) -> T): T {
        val title = getString(Res.string.core_export_making)
        val early = getString(Res.string.core_export_notify_progress_early, task.name, task.quality)
        val withLeft = getString(Res.string.core_export_notify_progress, task.name, task.quality, LEFT)
        var fraction = 0f
        val continued = IosBackground.continued
        val started =
            continued?.start("app.onlyshots.export.${task.id}", title, early, onExpired = task.onCancel) == true
        if (started) {
            var ok = false
            return try {
                block { status ->
                    fraction = status.fraction
                    continued!!.progress(
                        status.fraction.toDouble(),
                        status.left?.let { withLeft.replace(LEFT, it) } ?: early,
                    )
                }
                    .also { ok = true }
            } finally {
                continued!!.finish(ok)
            }
        }
        val paused = getString(Res.string.core_export_notify_paused, PERCENT)
        val pausedText = getString(Res.string.core_export_notify_paused_text)
        return shortWhile(
            task.name,
            onExpired = {
                // The time is up: stop it cleanly, and say so, before iOS stops the app.
                task.onCancel()
                post(
                    task.id,
                    paused.replace(PERCENT, "${(fraction * 100).toInt()}%"),
                    pausedText,
                )
            },
        ) {
            block { fraction = it.fraction }
        }
    }

    private suspend fun <T> shortWhile(title: String, onExpired: () -> Unit, block: suspend () -> T): T {
        val app = UIApplication.sharedApplication
        var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
        task =
            app.beginBackgroundTaskWithName(title) {
                onExpired()
                if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
                task = UIBackgroundTaskInvalid
            }
        return try {
            block()
        } finally {
            if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
        }
    }

    override suspend fun finished(end: ExportEnd) {
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) return
        when (end) {
            is ExportEnd.Saved ->
                post(
                    end.id,
                    getString(Res.string.core_export_notify_saved_photos),
                    getString(Res.string.core_export_notify_saved_text, end.name, end.length),
                )
            is ExportEnd.Failed ->
                post(
                    end.id,
                    getString(Res.string.core_export_notify_failed),
                    getString(Res.string.core_export_notify_failed_text, end.name, end.reason),
                )
            is ExportEnd.Paused -> Unit
        }
    }

    private companion object {
        const val LEFT = "\u0000left\u0000"
        const val PERCENT = "\u0000percent\u0000"
    }
}

/** A banner now, which opens the editor on [id] when tapped ([ExportNotificationTaps]). */
private fun post(id: String, title: String, body: String) {
    val content =
        UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(mapOf(VIDEO_KEY to id))
        }
    UNUserNotificationCenter.currentNotificationCenter()
        .addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(NSUUID().UUIDString, content, null),
            null,
        )
}

private const val VIDEO_KEY = "videoId"

/**
 * Taps on the export's banners: the editor on that video. [install]ed as the notification centre's delegate at launch;
 * the delegate is kept here, since the centre holds it weakly.
 */
object ExportNotificationTaps {
    private val delegate = TapsDelegate()

    fun install() {
        UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
    }
}

private class TapsDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        (didReceiveNotificationResponse.notification.request.content.userInfo[VIDEO_KEY] as? String)?.let {
            OpenRequests.ask(OpenRequests.Video(it))
        }
        withCompletionHandler()
    }

    /** In front the export screen says it all: no banner over it. */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(0u)
    }
}
