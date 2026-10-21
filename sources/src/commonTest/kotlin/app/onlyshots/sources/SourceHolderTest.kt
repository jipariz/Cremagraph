package app.onlyshots.sources

import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.model.summary
import app.onlyshots.sources.fake.FakeShotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A source whose events the test fires by hand, so nothing depends on replay timing. */
private class TestSource(
    override val kind: SourceKind = SourceKind.GAGGIUINO,
    private val shots: List<ShotSummary> = emptyList(),
) : ShotSource {
    private val _live = MutableSharedFlow<Sample>(extraBufferCapacity = 16)
    private val _brew = MutableSharedFlow<BrewEvent>(extraBufferCapacity = 16)

    override val capabilities = Capabilities.of(kind)
    override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val live: SharedFlow<Sample> = _live
    override val brewEvents: SharedFlow<BrewEvent> = _brew

    override suspend fun history(limit: Int) = shots.take(limit)

    override suspend fun loadShot(id: String): Shot = throw NoSuchElementException(id)

    fun emit(tUs: Long) = _live.tryEmit(Sample(tUs = tUs, brewing = true))
}

@OptIn(ExperimentalCoroutinesApi::class)
class SourceHolderTest {
    @Test
    fun theSampleIsForOneTakeAndPutsBackWhatItStoodInFor() = runTest {
        val none = TestSource().apply { connection.value = ConnectionState.Unreachable("no machine set up") }
        val holder = SourceHolder(backgroundScope, none)
        holder.useSample { FakeShotSource() }
        runCurrent()
        assertTrue(holder.sampling)
        assertIs<FakeShotSource>(holder.current.value)

        holder.endSample()
        runCurrent()
        assertTrue(!holder.sampling)
        assertEquals(none, holder.current.value)
    }

    @Test
    fun aSavedMachineOutOfReachNeverLeavesTheSampleBehind() = runTest {
        val none = TestSource().apply { connection.value = ConnectionState.Unreachable("no machine set up") }
        val holder = SourceHolder(backgroundScope, none)
        holder.use { error("the machine is off") }
        runCurrent()
        holder.useSample { FakeShotSource() }
        runCurrent()

        holder.endSample()
        runCurrent()
        // The machine is tried again; failing, the app is back on the disconnected source, not on the replay.
        assertEquals(none, holder.current.value)
        assertIs<ConnectionState.Unreachable>(holder.connection.value)
    }

    @Test
    fun eventsFollowTheSourceInUse() = runTest {
        val first = TestSource()
        val second = TestSource()
        val holder = SourceHolder(backgroundScope, first)
        val samples = mutableListOf<Long>()
        backgroundScope.launch { holder.live.collect { samples += it.tUs } }
        runCurrent()

        first.emit(1)
        runCurrent()
        assertEquals(listOf(1L), samples)

        holder.use { second }
        runCurrent()
        second.emit(2)
        runCurrent()
        assertEquals(listOf(1L, 2L), samples)

        // The machine that was swapped out must not reach the recorder any more.
        first.emit(3)
        runCurrent()
        assertEquals(listOf(1L, 2L), samples)
    }

    @Test
    fun connectionReportsConnectingUntilTheSourceIsBuilt() = runTest {
        val holder = SourceHolder(backgroundScope, FakeShotSource())
        runCurrent()
        assertEquals(ConnectionState.Connected, holder.connection.value)

        holder.use {
            awaitCancellation() // a scan that never finds anything
        }
        runCurrent()

        assertEquals(ConnectionState.Connecting, holder.connection.value)
    }

    @Test
    fun aSourceThatCannotBeBuiltIsUnreachable() = runTest {
        val holder = SourceHolder(backgroundScope, FakeShotSource())

        holder.use { null }
        runCurrent()
        advanceUntilIdle()

        val state = holder.connection.value
        assertIs<ConnectionState.Unreachable>(state)
    }

    @Test
    fun aFactoryThatThrowsReportsItsReason() = runTest {
        val holder = SourceHolder(backgroundScope, FakeShotSource())

        holder.use { error("no route to host") }
        runCurrent()
        advanceUntilIdle()

        assertEquals(ConnectionState.Unreachable("no route to host"), holder.connection.value)
    }

    @Test
    fun pauseCancelsTheConnectionButKeepsTheMachine() = runTest {
        val holder = SourceHolder(backgroundScope, FakeShotSource())
        var running = false
        holder.use { scope ->
            scope.launch {
                running = true
                try {
                    awaitCancellation()
                } finally {
                    running = false
                }
            }
            FakeShotSource()
        }
        runCurrent()
        advanceUntilIdle()
        assertTrue(running, "the connection never started")

        holder.pause()
        runCurrent()
        advanceUntilIdle()

        assertTrue(!running, "pause left the connection running")
        assertEquals(SourceKind.GAGGIUINO, holder.kind, "the machine's identity was lost on pause")
    }

    @Test
    fun historyComesFromTheCurrentSource() = runTest {
        val holder = SourceHolder(backgroundScope, FakeShotSource())
        val other = TestSource(shots = listOf(SampleShots.zer0.copy(id = "from-the-new-machine").summary()))

        holder.use { other }
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("from-the-new-machine"), holder.history().map { it.id })
    }

    @Test
    fun noHistoryFromTheSampleOrAMachineNotReached() = runTest {
        val holder = SourceHolder(backgroundScope, FakeShotSource())
        runCurrent()
        assertEquals(emptyList(), holder.history(), "the replayed sample is no machine")

        val away = TestSource(shots = listOf(SampleShots.zer0.summary()))
        away.connection.value = ConnectionState.Unreachable("gone")
        holder.use { away }
        runCurrent()
        advanceUntilIdle()
        assertEquals(emptyList(), holder.history(), "a machine not reached lists nothing")
    }
}
