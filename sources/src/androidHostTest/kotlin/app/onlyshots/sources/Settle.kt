package app.onlyshots.sources

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Collects [this] and returns once the collector is subscribed. `live` and `brewEvents` have no
 * replay, so anything emitted before the collector is subscribed is lost: an in-process fake
 * machine can send its first frame before a freshly launched collector has started.
 */
suspend fun <T> SharedFlow<T>.collectSubscribed(scope: CoroutineScope, onEach: suspend (T) -> Unit) {
    val subscribed = CompletableDeferred<Unit>()
    scope.launch { onSubscription { subscribed.complete(Unit) }.collect { onEach(it) } }
    subscribed.await()
}

/**
 * Samples and brew events travel on separate flows, so the end event can reach its collector
 * before the last reading reaches the other one. Wait for the readings to stop arriving before
 * asserting on the shot.
 *
 * A quiet window alone is a race on a loaded machine: a straggler more than [quietMs] late looks
 * like the end of the stream. Tests that know how many readings the fake machine sent pass
 * [expected], which turns that race into a timeout only when a reading is genuinely lost.
 */
suspend fun LiveShotAssembler.settle(expected: Int? = null, quietMs: Long = 100, timeoutMs: Long = 5_000) {
    withTimeout(timeoutMs) {
        var seen = -1
        while (true) {
            val n = progress().samples.size
            if (expected != null && n < expected) {
                delay(10)
                continue
            }
            if (n == seen) return@withTimeout
            seen = n
            delay(quietMs)
        }
    }
}
