package app.onlyshots.home

import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.Gaggiuino
import app.onlyshots.sources.machine.MdnsBrowser
import app.onlyshots.sources.machine.NetworkScan
import app.onlyshots.sources.machine.createMachineHttpClient
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.SettingsStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class MachineShotsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val client = createMachineHttpClient()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        MachineShotsViewModel(
            client,
            NetworkScan(client, MdnsBrowser.None, { emptyList() }, listOf(Gaggiuino)),
            AppSettingsRepo(SettingsStore(FakeFileSystem(), "/settings.json".toPath())),
        )

    @Test
    fun backFromAMachinesShotsReturnsToTheMachinesAndFromThereLeaves() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.chooseMachine(FoundMachine(Gaggiuino, "192.0.2.1", 80))
            assertTrue(vm.back(), "from the shots, back is the machines")
            assertNull(vm.state.value.machine)
            assertNull(vm.state.value.shots)
            assertFalse(vm.back(), "from the machines, back leaves the screen")
            assertEquals(MachineShotsUiState(), vm.state.value)
        }
}
