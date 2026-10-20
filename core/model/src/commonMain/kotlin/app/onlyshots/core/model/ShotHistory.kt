package app.onlyshots.core.model

/**
 * Shots a machine kept, for attaching one to a video in the editor. A connected machine's source is one; an editor with
 * no machine (the desktop and web editors) has [None].
 */
interface ShotHistory {
    suspend fun history(limit: Int = 50): List<ShotSummary>

    suspend fun loadShot(id: String): Shot

    /** No machine: nothing to list. */
    object None : ShotHistory {
        override suspend fun history(limit: Int): List<ShotSummary> = emptyList()

        override suspend fun loadShot(id: String): Shot = throw NoSuchElementException("no machine")
    }
}
