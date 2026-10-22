package app.onlyshots.connect

import app.onlyshots.core.model.SourceKind
import app.onlyshots.devTools
import app.onlyshots.machine.MachineLog
import app.onlyshots.sources.ShotSource
import app.onlyshots.sources.ble.BleScan
import app.onlyshots.sources.ble.KableBleDeviceLink
import app.onlyshots.sources.ble.ScaleShotSource
import app.onlyshots.sources.machine.NetworkMachines
import app.onlyshots.storage.AppSettings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

/** How a source is built from a saved choice: the one place that maps a machine to its code. */
typealias SourceFactory = suspend (CoroutineScope) -> ShotSource?

/**
 * The factory for a saved setup, or null when nothing usable is configured — no machine chosen, an address that is not
 * a host, a scale that was never picked.
 */
fun sourceFactory(settings: AppSettings, client: HttpClient): SourceFactory? =
    factoryFor(settings.source ?: return null, settings.host.orEmpty(), settings.scaleId, client)

fun factoryFor(kind: SourceKind, address: String, scaleId: String?, client: HttpClient): SourceFactory? {
    if (kind == SourceKind.SCALE) {
        val id = scaleId ?: return null
        return { scope ->
            // The Kable link needs a live advertisement, so a saved scale is found by scanning
            // again rather than by storing anything about the device itself.
            BleScan.find(id)?.let { device ->
                val link = KableBleDeviceLink(device).also { it.start(scope) }
                val log: ((String) -> Unit)? = if (devTools) ({ println("OnlyShotsScale $it") }) else null
                ScaleShotSource(link, log = log).also { it.start(scope) }
            }
        }
    }
    // A machine saved by an older version that this one no longer supports starts disconnected.
    val machine = NetworkMachines.of(kind) ?: return null
    val (host, port) = MachineLog.parseAddress(address, machine.defaultPort) ?: return null
    return { scope -> machine.source(client, host, port).also { it.start(scope) } }
}
