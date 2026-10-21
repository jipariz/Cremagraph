package app.onlyshots.home

import androidx.lifecycle.ViewModelStore
import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.SourceHolder
import app.onlyshots.sources.ble.FoundBleDevice
import app.onlyshots.sources.fake.FakeShotSource
import app.onlyshots.sources.machine.AutoStart
import app.onlyshots.sources.machine.MdnsBrowser
import app.onlyshots.sources.machine.MdnsService
import app.onlyshots.sources.machine.NetworkMachine
import app.onlyshots.sources.machine.NetworkScan
import app.onlyshots.sources.machine.createMachineHttpClient
import app.onlyshots.sources.machine.gaggiuinoSource
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.SettingsStore
import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val client = createMachineHttpClient()

    /** A machine at whichever addresses [at] lists when it is asked; a test changes it between searches. */
    private class MovingMachine(var at: Set<String>) : NetworkMachine {
        override val kind = SourceKind.GAGGIUINO
        override val name = "Gaggiuino"
        override val defaultPort = 80
        override val hostnames = emptyList<String>()
        override val autoStart = AutoStart.BREW_SWITCH

        override fun matchesService(service: MdnsService) = false

        override suspend fun identify(client: HttpClient, host: String, port: Int, connectTimeoutMs: Long) = host in at

        override fun source(client: HttpClient, host: String, port: Int) = gaggiuinoSource(client, host, port)
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        machine: MovingMachine,
        holder: SourceHolder = SourceHolder(backgroundScope, FakeShotSource()),
        scales: () -> Flow<FoundBleDevice>,
    ): HomeViewModel {
        val settings = AppSettingsRepo(SettingsStore(FakeFileSystem(), "/settings.json".toPath()))
        val scan = NetworkScan(client, MdnsBrowser.None, { listOf("192.168.1.20") }, listOf(machine))
        return HomeViewModel(holder, settings, client, scan, scales, pauseBetweenSearchesMs = 1_000)
    }

    @Test
    fun aMachineStaysUntilAWholeSearchMissesIt() =
        runTest(dispatcher) {
            val machine = MovingMachine(setOf("192.168.1.7"))
            val vm = viewModel(machine) { emptyFlow() }
            vm.startSearching()
            runCurrent()
            assertEquals(listOf("192.168.1.7"), vm.state.value.machines.map { it.address })

            machine.at = setOf("192.168.1.8")
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(listOf("192.168.1.8"), vm.state.value.machines.map { it.address })
            vm.stopSearching()
        }

    @Test
    fun bluetoothOffIsShownAndRetriedInsteadOfCrashing() =
        runTest(dispatcher) {
            var attempts = 0
            val vm =
                viewModel(MovingMachine(emptySet())) {
                    flow {
                        attempts++
                        error("This device doesn't support the Bluetooth low energy central or client role")
                    }
                }
            vm.onBluetoothAnswer(granted = true)
            vm.startSearching()
            runCurrent()
            assertTrue(vm.state.value.bluetoothOff)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(2, attempts)
            assertFalse(vm.state.value.bluetoothDenied)
            vm.stopSearching()
        }

    @Test
    fun backOnHomeTheSampleIsOver() =
        runTest(dispatcher) {
            val initial = FakeShotSource()
            val holder = SourceHolder(backgroundScope, initial)
            val vm = viewModel(MovingMachine(emptySet()), holder) { emptyFlow() }
            vm.useSample()
            runCurrent()
            assertNotSame(initial, holder.current.value)
            // Home says nothing of it: the record button stays the one for no machine.
            assertEquals(recordAction(HomeUiState()), recordAction(vm.state.value))

            vm.endSample()
            runCurrent()
            assertSame(initial, holder.current.value)
        }

    @Test
    fun leavingHomeEndsTheSample() =
        runTest(dispatcher) {
            val initial = FakeShotSource()
            val holder = SourceHolder(backgroundScope, initial)
            val vm = viewModel(MovingMachine(emptySet()), holder) { emptyFlow() }
            val store = ViewModelStore().apply { put("home", vm) }
            vm.useSample()
            runCurrent()
            assertNotSame(initial, holder.current.value)

            // Nothing was set up, so the app is back on the source it started with.
            store.clear()
            runCurrent()
            assertSame(initial, holder.current.value)
        }
}
