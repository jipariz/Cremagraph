package app.onlyshots.platform

/** Keeps the process alive while a render runs, so leaving the app does not cancel it. */
interface BackgroundWork {
    suspend fun <T> keepAlive(title: String, block: suspend () -> T): T
}
