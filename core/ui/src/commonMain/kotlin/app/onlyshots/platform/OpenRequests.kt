package app.onlyshots.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A video to show, asked for from outside the app: an export notification tapped. The platform's entry (the Android
 * activity, the iOS notification delegate) [ask]s; the app's navigation [take]s it and opens the editor on it.
 */
object OpenRequests {
    /** The editor on [id]; [saved] when it was just put in the gallery, so the editor opens on its done state. */
    data class Video(val id: String, val saved: SavedVideo? = null)

    private val pending = MutableStateFlow<Video?>(null)

    /** What is waiting to be opened, if anything. */
    val video: StateFlow<Video?> = pending.asStateFlow()

    fun ask(video: Video) {
        pending.value = video
    }

    /** The waiting request, now handled. */
    fun take(): Video? = pending.value.also { pending.value = null }
}
