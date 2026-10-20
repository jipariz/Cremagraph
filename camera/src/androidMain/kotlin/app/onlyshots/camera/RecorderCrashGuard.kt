package app.onlyshots.camera

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.ref.WeakReference

/**
 * Keeps one CameraX failure from bringing the app down. CameraX's `Recorder` throws an `AssertionError` on its own
 * thread when the camera drops out (the camera service restarting, a rebind) while a recording is starting during a
 * reset: "In-progress recording error occurred while in unexpected state: CONFIGURING". It is still there in 1.6. The
 * recorder is wedged after it, so [AndroidCameraController.recoverFromRecorderFailure] swaps in a new one.
 *
 * Every other uncaught exception goes on to the handler there was before.
 */
internal object RecorderCrashGuard {
    private var controller = WeakReference<AndroidCameraController>(null)
    private var installed = false

    @Synchronized
    fun install(current: AndroidCameraController) {
        controller = WeakReference(current)
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val main = Handler(Looper.getMainLooper())
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            if (isRecorderAssertion(e)) {
                Log.w(TAG, "CameraX recorder failed on ${thread.name}; recovering", e)
                main.post { controller.get()?.recoverFromRecorderFailure() }
            } else {
                previous?.uncaughtException(thread, e)
            }
        }
    }

    /** CameraX's recorder asserting on its own state: nothing of ours is wrong, and nothing else is affected. */
    fun isRecorderAssertion(e: Throwable): Boolean =
        e is AssertionError && e.stackTrace.any { it.className.startsWith(RECORDER) }

    private const val RECORDER = "androidx.camera.video.Recorder"
    private const val TAG = "OnlyShotsCamera"
}
