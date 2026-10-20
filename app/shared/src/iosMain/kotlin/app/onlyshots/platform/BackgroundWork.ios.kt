package app.onlyshots.platform

import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * ponytail: iOS grants a background task a few minutes. A long 4K render that is backgrounded pauses and finishes when
 * the app comes back; a BGProcessingTask would be the upgrade.
 */
class BackgroundTaskWork : BackgroundWork {
    override suspend fun <T> keepAlive(title: String, block: suspend () -> T): T {
        val app = UIApplication.sharedApplication
        val task = app.beginBackgroundTaskWithName(title, null)
        return try {
            block()
        } finally {
            if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
        }
    }
}
