package app.onlyshots.sources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Thrown by a session when the machine refuses the connection because it is full. */
class SourceBusyException(message: String = ConnectionState.Busy.MESSAGE) : Exception(message)

const val RETRY_INTERVAL_MS = 5_000L

/**
 * Keeps one connection alive: runs [session] (which suspends for as long as the connection is open and calls
 * `onConnected` once it is), and after it ends or fails waits [retryMs] and tries again. Runs until the calling scope
 * is cancelled, so tie it to the app being in the foreground.
 */
suspend fun keepConnected(
    state: MutableStateFlow<ConnectionState>,
    retryMs: Long = RETRY_INTERVAL_MS,
    session: suspend (onConnected: () -> Unit) -> Unit,
) {
    while (true) {
        if (state.value !is ConnectionState.Busy && state.value !is ConnectionState.Unreachable) {
            state.value = ConnectionState.Connecting
        }
        try {
            session { state.value = ConnectionState.Connected }
            state.value = ConnectionState.Unreachable("connection closed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceBusyException) {
            state.value = ConnectionState.Busy
        } catch (e: Exception) {
            state.value = ConnectionState.Unreachable(e.message ?: e::class.simpleName ?: "unreachable")
        }
        delay(retryMs)
    }
}
