package app.onlyshots.sources

import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.machine.AutoStart
import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.Gaggiuino
import app.onlyshots.sources.machine.MdnsBrowser
import app.onlyshots.sources.machine.MdnsService
import app.onlyshots.sources.machine.NetworkMachine
import app.onlyshots.sources.machine.NetworkScan
import app.onlyshots.sources.machine.createMachineHttpClient
import app.onlyshots.sources.machine.gaggiuinoSource
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class NetworkScanTest {
    private val client = createMachineHttpClient()

    /** A machine that is wherever [at] says, with every probe recorded. */
    private class FakeMachine(private val at: Set<String>) : NetworkMachine {
        val probed = mutableListOf<String>()
        override val kind = SourceKind.GAGGIUINO
        override val name = "Fake"
        override val defaultPort = 80
        override val hostnames = listOf("fake.local")
        override val autoStart = AutoStart.BREW_SWITCH

        override fun matchesService(service: MdnsService) = service.name.startsWith("fake")

        override suspend fun identify(client: HttpClient, host: String, port: Int, connectTimeoutMs: Long): Boolean {
            probed += "$host:$port"
            return "$host:$port" in at
        }

        override fun source(client: HttpClient, host: String, port: Int) = gaggiuinoSource(client, host, port)
    }

    private fun scan(
        machine: FakeMachine,
        own: List<String> = listOf("192.168.1.20"),
        mdns: List<MdnsService> = emptyList(),
        resolve: suspend (String) -> String? = { null },
    ) = NetworkScan(client, MdnsBrowser { flowOf(*mdns.toTypedArray()) }, { own }, listOf(machine), resolve = resolve)

    @Test
    fun sweepsEveryOtherHostOfTheDevicesPrivateSubnet() {
        val hosts = NetworkScan.sweepHosts(listOf("192.168.1.20", "127.0.0.1", "100.64.3.2"))
        assertEquals(253, hosts.size)
        assertFalse("192.168.1.20" in hosts)
        assertTrue("192.168.1.1" in hosts && "192.168.1.254" in hosts)
        assertEquals(252, NetworkScan.sweepHosts(listOf("10.0.0.5", "10.0.0.6")).size)
        assertTrue(NetworkScan.sweepHosts(listOf("8.8.8.8")).isEmpty())
    }

    @Test
    fun aMachineOnTheSubnetIsFound() = runTest {
        val found = scan(FakeMachine(setOf("192.168.1.7:80"))).machines().toList()
        assertEquals(listOf("192.168.1.7"), found.map(FoundMachine::address))
    }

    @Test
    fun theFixedNameIsTriedOnlyWhenNothingElseFoundIt() = runTest {
        val onSubnet = FakeMachine(setOf("192.168.1.7:80", "fake.local:80"))
        assertEquals(listOf("192.168.1.7"), scan(onSubnet).machines().toList().map { it.address })
        assertFalse("fake.local:80" in onSubnet.probed)

        val byNameOnly = FakeMachine(setOf("fake.local:80"))
        assertEquals(listOf("fake.local"), scan(byNameOnly, own = emptyList()).machines().toList().map { it.address })
    }

    @Test
    fun mdnsServicesAreFilteredAndNotProbedTwice() = runTest {
        val machine = FakeMachine(setOf("192.168.1.7:80", "192.168.1.9:8080"))
        val mdns =
            listOf(
                MdnsService("fake-espresso", "_http._tcp.", "192.168.1.7", 80),
                MdnsService("fake-espresso", "_http._tcp.", "192.168.1.9", 8080),
                MdnsService("printer", "_http._tcp.", "192.168.1.30", 80),
            )
        val found = scan(machine, mdns = mdns).machines().toList()
        assertEquals(setOf("192.168.1.7", "192.168.1.9:8080"), found.map { it.address }.toSet())
        assertEquals(1, machine.probed.count { it == "192.168.1.7:80" })
    }

    @Test
    fun gaggiuinoIsRecognisedByItsFrames() {
        assertTrue(Gaggiuino.isGaggiuinoFrame(GaggiuinoFrames.sensor(brewActive = false)))
        assertTrue(Gaggiuino.isGaggiuinoFrame(GaggiuinoFrames.shot(0)))
        assertFalse(Gaggiuino.isGaggiuinoFrame(GaggiuinoFrames.LOG))
        assertFalse(Gaggiuino.isGaggiuinoFrame("""{"tp":"evt:status"}"""))
        assertFalse(Gaggiuino.isGaggiuinoFrame("<html>"))
    }

    @Test
    fun aNameThatResolvesToTheSweptIpIsOneMachineWithItsName() = runTest {
        val machine = FakeMachine(setOf("192.168.1.7:80"))
        val found = scan(machine, resolve = { if (it == "fake.local") "192.168.1.7" else null }).machines().toList()
        val only = found.single()
        assertEquals("192.168.1.7", only.address)
        assertEquals("fake.local · 192.168.1.7", only.label)
        assertTrue(only.isAt("fake.local") && only.isAt("192.168.1.7"))
        assertEquals(1, machine.probed.count { it == "192.168.1.7:80" })
        assertFalse("fake.local:80" in machine.probed)
    }

    @Test
    fun theConnectedMachineIsReportedWithoutAProbe() = runTest {
        val machine = FakeMachine(emptySet())
        val found =
            scan(machine, resolve = { "192.168.1.7".takeIf { _ -> it == "fake.local" } })
                .machines(trusted = setOf("fake.local"))
                .toList()
        assertEquals(listOf("192.168.1.7"), found.map { it.address })
        assertFalse("192.168.1.7:80" in machine.probed)
    }
}
