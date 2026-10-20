package app.onlyshots.sources.machine

import io.ktor.client.HttpClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A machine [NetworkScan] has confirmed with [NetworkMachine.identify]. [host] is what to save and connect to — an IP
 * whenever one is known — and [hostname] the machine's own name when it resolves to that IP.
 */
data class FoundMachine(val machine: NetworkMachine, val host: String, val port: Int, val hostname: String? = null) {
    /** What the address field would hold: the port only when it is not the machine's default. */
    val address: String
        get() = withPort(host)

    /** "gaggiuino.local · 10.0.1.42", or just the one that is known. */
    val label: String
        get() = listOfNotNull(hostname?.let(::withPort), address).distinct().joinToString(" · ")

    /** Whether [address] (as saved, a name or an IP) is this machine. */
    fun isAt(address: String): Boolean = address == this.address || hostname?.let { withPort(it) == address } == true

    private fun withPort(h: String) = if (port == machine.defaultPort) h else "$h:$port"
}

/** An mDNS service, resolved to an address. */
data class MdnsService(val name: String, val type: String, val host: String, val port: Int)

/** Browses mDNS on platforms that have an API for it; iOS and the desktop resolve `.local` names natively instead. */
fun interface MdnsBrowser {
    /** Resolved services of [type] (e.g. `_http._tcp.`) for as long as it is collected. */
    fun browse(type: String): Flow<MdnsService>

    companion object {
        val None = MdnsBrowser { emptyFlow() }
    }
}

/** The device's own IPv4 addresses on its local networks, dotted. */
expect fun localIpv4Addresses(): List<String>

/** [host]'s IPv4 address through the platform resolver (which handles `.local` on iOS, macOS and recent Android). */
expect suspend fun resolveIpv4(host: String): String?

/**
 * Finds machines on the local network, so nobody has to type an address. Candidates come from a machine's own
 * [NetworkMachine.hostnames] resolved to IPs, from mDNS, and from a sweep of the device's own /24; each is confirmed
 * with [NetworkMachine.identify] before it is reported. Everything is keyed by IP, so a machine found both by name and
 * by the sweep is reported once, with its name attached. A name that does not resolve is probed as it is, last, and
 * only when nothing else found that machine.
 *
 * The flow completes when every candidate has been checked; collectors usually also cap it with [SCAN_TIMEOUT_MS].
 */
class NetworkScan(
    private val client: HttpClient,
    private val mdns: MdnsBrowser = MdnsBrowser.None,
    private val localAddresses: () -> List<String> = ::localIpv4Addresses,
    private val machines: List<NetworkMachine> = NetworkMachines.all,
    private val sweepPorts: (NetworkMachine) -> List<Int> = { listOf(it.defaultPort) },
    private val resolve: suspend (String) -> String? = ::resolveIpv4,
) {
    /**
     * The machines on the network. [trusted] are addresses (as saved: a name or an IP) of machines already connected,
     * which are reported without a probe: probing takes one of the few sockets the machine has, from its own app.
     */
    fun machines(trusted: Set<String> = emptySet()): Flow<FoundMachine> = channelFlow {
        val seen = mutableSetOf<String>()
        val found = mutableSetOf<NetworkMachine>()
        val lock = Mutex()
        val gate = Semaphore(PARALLEL_PROBES)

        // "ip:port" to the name that resolved to it, so whichever way the machine is reached it is named.
        val names = mutableMapOf<String, String>()
        val unresolved = mutableListOf<Pair<NetworkMachine, String>>()
        coroutineScope {
            for (machine in machines) for (name in machine.hostnames) {
                launch {
                    val ip = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolve(name) }
                    lock.withLock {
                        if (ip != null) names["$ip:${machine.defaultPort}"] = name else unresolved += machine to name
                    }
                }
            }
        }

        suspend fun check(machine: NetworkMachine, host: String, port: Int, connectTimeoutMs: Long) {
            if (!lock.withLock { seen.add("$host:$port") }) return
            val candidate = FoundMachine(machine, host, port, lock.withLock { names["$host:$port"] })
            val ok =
                trusted.any(candidate::isAt) ||
                    gate.withPermit { machine.identify(client, host, port, connectTimeoutMs) }
            if (ok) {
                lock.withLock { found += machine }
                send(candidate)
            }
        }

        coroutineScope {
            val probes = this
            for (key in names.keys) {
                val ip = key.substringBeforeLast(':')
                val port = key.substringAfterLast(':').toInt()
                for (machine in machines.filter { it.defaultPort == port }) {
                    launch { check(machine, ip, port, NAMED_CONNECT_TIMEOUT_MS) }
                }
            }
            // mDNS keeps browsing until cancelled, so it is given the sweep's lifetime; probes it started still finish.
            val browse = launch {
                mdns.browse(HTTP_SERVICE).collect { service ->
                    for (machine in machines) {
                        if (machine.matchesService(service)) {
                            probes.launch { check(machine, service.host, service.port, NAMED_CONNECT_TIMEOUT_MS) }
                        }
                    }
                }
            }
            coroutineScope {
                for (host in sweepHosts(localAddresses())) for (machine in machines) for (port in sweepPorts(machine)) {
                    launch { check(machine, host, port, SWEEP_CONNECT_TIMEOUT_MS) }
                }
            }
            browse.cancel()
        }
        coroutineScope {
            val missing = lock.withLock { unresolved.filter { (machine, _) -> machine !in found } }
            for ((machine, name) in missing) {
                launch { check(machine, name, machine.defaultPort, NAMED_CONNECT_TIMEOUT_MS) }
            }
        }
    }

    companion object {
        const val SCAN_TIMEOUT_MS = 15_000L
        const val HTTP_SERVICE = "_http._tcp."
        const val SWEEP_CONNECT_TIMEOUT_MS = 600L
        const val NAMED_CONNECT_TIMEOUT_MS = 3_000L
        const val RESOLVE_TIMEOUT_MS = 2_000L
        const val PARALLEL_PROBES = 48

        /**
         * Every other host of each private /24 the device is on. A wider network is still swept as its own /24 only:
         * home networks are /24, and 65 000 probes would take minutes.
         */
        internal fun sweepHosts(own: List<String>): List<String> =
            own.filter(::isPrivate)
                .map { it.substringBeforeLast('.') }
                .distinct()
                .take(MAX_SUBNETS)
                .flatMap { prefix -> (1..254).map { "$prefix.$it" } }
                .filterNot { it in own }

        private fun isPrivate(ip: String): Boolean {
            val o = ip.split('.').mapNotNull { it.toIntOrNull() }
            if (o.size != 4) return false
            return o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)
        }

        private const val MAX_SUBNETS = 2
    }
}
