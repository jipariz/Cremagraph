package app.onlyshots.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectTest {
    @Test
    fun retriesEveryFiveSecondsAndReportsBusy() = runTest {
        val state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
        var attempts = 0
        val hold = CompletableDeferred<Unit>()
        val job = launch {
            keepConnected(state) { onConnected ->
                attempts++
                when (attempts) {
                    1 -> throw IllegalStateException("no route to host")
                    2 -> throw SourceBusyException()
                    else -> {
                        onConnected()
                        hold.await()
                    }
                }
            }
        }
        runCurrent()
        assertEquals(1, attempts)
        assertEquals(ConnectionState.Unreachable("no route to host"), state.value)
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, attempts)
        assertIs<ConnectionState.Busy>(state.value)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(ConnectionState.Connected, state.value)
        hold.complete(Unit)
        runCurrent()
        assertIs<ConnectionState.Unreachable>(state.value)
        job.cancel()
    }
}
