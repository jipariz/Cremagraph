package app.onlyshots.platform

/** Keeps the process alive while a render runs, so leaving the app does not cancel it. */
interface BackgroundWork {
    suspend fun <T> keepAlive(title: String, block: suspend () -> T): T

    /**
     * Keeps the process alive while [task] renders and shows it outside the app where the platform can: a progress
     * notification on Android, the system's progress on iOS 26, the tab title on the web. [block] reports each step
     * through the function it is handed; the user stopping it from there calls [ExportTask.onCancel]. By default only
     * kept alive, shown nowhere.
     */
    suspend fun <T> keepAlive(task: ExportTask, block: suspend (report: (ExportStatus) -> Unit) -> T): T =
        keepAlive(task.name) { block {} }

    /**
     * The render is over: say how it went outside the app, where the user may be. Nothing while the app is in front.
     */
    suspend fun finished(end: ExportEnd) {}
}

/** A render shown outside the app: the video's [id], its [name] and the [quality] it is made at ("1080p"). */
data class ExportTask(val id: String, val name: String, val quality: String, val onCancel: () -> Unit)

/** How far it is: [fraction] 0..1, and [left] ("0:31") once there is a fair guess. */
data class ExportStatus(val fraction: Float, val left: String?)

/** How a render shown outside the app ended. A cancelled one ends with nothing to say. */
sealed interface ExportEnd {
    val id: String
    val name: String

    /** In the gallery: [saved] opens it again, where the platform can; [length] is the video's ("0:47"). */
    data class Saved(override val id: String, override val name: String, val length: String, val saved: SavedVideo?) :
        ExportEnd

    /** Not made, for [reason]. */
    data class Failed(override val id: String, override val name: String, val reason: String) : ExportEnd

    /** Stopped part way by the platform (iOS before 26 ran out of background time), at [fraction]. */
    data class Paused(override val id: String, override val name: String, val fraction: Float) : ExportEnd
}
