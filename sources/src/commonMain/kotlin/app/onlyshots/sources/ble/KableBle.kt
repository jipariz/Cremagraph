package app.onlyshots.sources.ble

import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.RETRY_INTERVAL_MS
import app.onlyshots.sources.keepConnected
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.PlatformAdvertisement
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.indicate
import com.juul.kable.notify
import com.juul.kable.write
import com.juul.kable.writeWithoutResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A scale seen while scanning that one of the [BleProtocols] can talk to. */
class FoundBleDevice(val advertisement: Advertisement, val protocol: BleProtocol) {
    val name: String
        get() = advertisement.name ?: protocol.label

    val id: String
        get() = advertisement.identifier.toString()
}

@OptIn(ExperimentalUuidApi::class)
object BleScan {
    /**
     * The device with this id, or null if it does not advertise within [timeoutMs]. A saved scale is reconnected this
     * way: the advertisement cannot be persisted, only the id can.
     */
    suspend fun find(id: String, timeoutMs: Long = SCAN_TIMEOUT_MS): FoundBleDevice? =
        withTimeoutOrNull(timeoutMs) { devices().first { it.id == id } }

    const val SCAN_TIMEOUT_MS = 15_000L

    /** Supported scales nearby, each reported once. */
    fun devices(scanner: Scanner<PlatformAdvertisement> = Scanner()): Flow<FoundBleDevice> {
        val seen = HashSet<String>()
        return scanner.advertisements.mapNotNull { adv ->
            val protocol = BleProtocols.find(adv.name, adv.uuids.map { it.toString() }) ?: return@mapNotNull null
            if (seen.add(adv.identifier.toString())) FoundBleDevice(adv, protocol) else null
        }
    }
}

/**
 * Kable link to one scale: connects, finds the protocol's GATT route, subscribes, sends the connect commands and
 * heartbeat, and decodes notifications. Reconnects every 5 s while [start]'s scope lives.
 */
@OptIn(ExperimentalUuidApi::class)
class KableBleDeviceLink(private val device: FoundBleDevice) : BleDeviceLink {
    override val name: String
        get() = device.name

    override val protocol: BleProtocol
        get() = device.protocol

    private val peripheral = Peripheral(device.advertisement)
    private val _readings = MutableSharedFlow<BleReading>(extraBufferCapacity = 256)
    override val readings: SharedFlow<BleReading> = _readings.asSharedFlow()
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private var route: GattRoute? = null

    fun start(scope: CoroutineScope): Job = scope.launch {
        keepConnected(_connection, RETRY_INTERVAL_MS) { onConnected -> session(onConnected) }
    }

    private suspend fun session(onConnected: () -> Unit) {
        try {
            peripheral.connect()
            coroutineScope {
                val services = peripheral.services.value.orEmpty()
                val present = services.map { it.serviceUuid.toString().lowercase() }.toSet()
                val r =
                    protocol.routes(name).firstOrNull { rt ->
                        rt.service in present && (rt.writeService == null || rt.writeService in present)
                    } ?: error("${protocol.label}: none of the known services on $name")
                route = r
                // Subscribe to the route's characteristics that can notify or indicate.
                val notifying =
                    services
                        .first { it.serviceUuid.toString().lowercase() == r.service }
                        .characteristics
                        .filter { it.properties.notify || it.properties.indicate }
                        .map { it.characteristicUuid.toString().lowercase() }
                        .toSet()
                val decoder = protocol.newDecoder(name)
                for (n in r.notify.filter { it in notifying }) launch {
                    peripheral.observe(characteristicOf(Uuid.parse(r.service), Uuid.parse(n))).collect { bytes ->
                        for (reading in decoder.feed(bytes)) _readings.emit(reading)
                    }
                }
                write(r, protocol.onConnect(r))
                onConnected()
                protocol.heartbeatMs?.let { ms ->
                    launch {
                        while (isActive) {
                            delay(ms)
                            write(r, protocol.heartbeat(r))
                        }
                    }
                }
                peripheral.state.filterIsInstance<State.Disconnected>().first()
                error("$name disconnected")
            }
        } finally {
            route = null
            withContext(NonCancellable) { peripheral.disconnect() }
        }
    }

    private suspend fun write(r: GattRoute, writes: List<BleWrite>) {
        for (w in writes) {
            val service = if (w.characteristic == null) r.writeService ?: r.service else r.service
            val characteristic = w.characteristic ?: r.write
            peripheral.write(
                characteristicOf(Uuid.parse(service), Uuid.parse(characteristic)),
                w.bytes,
                writeType(service, characteristic, w.withResponse),
            )
        }
    }

    /** The requested write type, or the other one when the characteristic only supports that (Wilfa Gen 1). */
    private fun writeType(service: String, characteristic: String, withResponse: Boolean): WriteType {
        val props =
            peripheral.services.value
                ?.firstOrNull { it.serviceUuid.toString().lowercase() == service }
                ?.characteristics
                ?.firstOrNull { it.characteristicUuid.toString().lowercase() == characteristic }
                ?.properties
        val response =
            when {
                props == null -> withResponse
                withResponse && !props.write && props.writeWithoutResponse -> false
                !withResponse && !props.writeWithoutResponse && props.write -> true
                else -> withResponse
            }
        return if (response) WriteType.WithResponse else WriteType.WithoutResponse
    }

    override suspend fun tare() {
        val r = route ?: return
        write(r, protocol.tare(r))
    }
}
