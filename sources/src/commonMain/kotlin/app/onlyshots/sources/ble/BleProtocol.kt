package app.onlyshots.sources.ble

/** A full 128-bit UUID for a 16-bit Bluetooth SIG short UUID. */
fun uuid16(short: String): String = "0000${short.lowercase().padStart(4, '0')}-0000-1000-8000-00805f9b34fb"

/**
 * Where a device's data lives. Protocols list candidates; the first route whose services are all present wins.
 * [writeService] is set when the write characteristic lives in another service than the notifications (Wilfa Gen 1).
 */
data class GattRoute(
    val service: String,
    val notify: List<String>,
    val write: String,
    val writeService: String? = null,
)

/** One write to the device. [characteristic] defaults to the route's write characteristic. */
class BleWrite(val bytes: ByteArray, val characteristic: String? = null, val withResponse: Boolean = false) {
    override fun toString(): String = "BleWrite(${bytes.toHex()} → ${characteristic ?: "route"})"
}

/** What one or more notifications meant. Fields the device did not send are null. */
data class BleReading(
    val weightG: Float? = null,
    /** Gravimetric flow the device computed itself. */
    val weightFlowGps: Float? = null,
    val batteryPercent: Int? = null,
    val timerMs: Long? = null,
)

/** Turns a notification byte stream back into readings; keeps partial frames between calls. */
fun interface BleDecoder {
    fun feed(bytes: ByteArray): List<BleReading>
}

/**
 * One family of standalone Bluetooth scales: how to find them, talk to them and read them. Pure, no BLE stack.
 * Bluetooth is for scales only; pressure comes from machines with a network API, so the phone never has to take a
 * device away from its own app.
 */
interface BleProtocol {
    val id: String
    val label: String

    fun matches(advertisedName: String?, serviceUuids: List<String> = emptyList()): Boolean

    fun routes(deviceName: String): List<GattRoute>

    fun onConnect(route: GattRoute): List<BleWrite> = emptyList()

    /** Interval for [heartbeat], or null for devices that need none. */
    val heartbeatMs: Long?
        get() = null

    fun heartbeat(route: GattRoute): List<BleWrite> = emptyList()

    fun tare(route: GattRoute): List<BleWrite> = emptyList()

    fun newDecoder(deviceName: String): BleDecoder
}

object BleProtocols {
    /** In matching order. */
    val all: List<BleProtocol> =
        listOf(
            AcaiaProtocol,
            BookooScaleProtocol,
            DecentScaleProtocol,
            EspressiScaleProtocol,
            FelicitaProtocol,
            TimemoreProtocol,
            TimemoreDotProtocol,
            DifluidProtocol,
            EclairProtocol,
            IkapeProtocol,
            MyScaleProtocol,
            PrecisaProtocol,
            TencentProtocol,
            VariaProtocol,
            WeighMyBruProtocol,
            WilfaProtocol,
            // Matches "863A" anywhere in the name, so it goes after every prefix match.
            InsmartProtocol,
        )

    fun find(advertisedName: String?, serviceUuids: List<String> = emptyList()): BleProtocol? = all.firstOrNull {
        it.matches(advertisedName, serviceUuids.map(String::lowercase))
    }
}

internal fun ByteArray.toHex(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

internal fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }

/** XOR of every byte except the last, as Bookoo and Decent Scale checksum their frames. */
internal fun xorChecksum(frame: ByteArray, until: Int = frame.size - 1): Int {
    var x = 0
    for (i in 0 until until) x = x xor frame.u8(i)
    return x
}

/** Sum of every byte in [from, until) modulo 256, as Difluid, iKape and Tencent checksum their frames. */
internal fun sumChecksum(frame: ByteArray, from: Int = 0, until: Int = frame.size - 1): Int {
    var s = 0
    for (i in from until until) s += frame.u8(i)
    return s and 0xFF
}

/** Accumulates notification bytes; frames can be split or glued across notifications. */
internal class ByteBuffer {
    private var buf = ByteArray(0)
    val size: Int
        get() = buf.size

    operator fun get(i: Int): Int = buf.u8(i)

    fun append(b: ByteArray) {
        buf += b
    }

    fun take(n: Int): ByteArray = buf.copyOfRange(0, n).also { drop(n) }

    fun peek(n: Int): ByteArray = buf.copyOfRange(0, n)

    fun drop(n: Int) {
        buf = buf.copyOfRange(minOf(n, buf.size), buf.size)
    }

    fun clear() {
        buf = ByteArray(0)
    }
}
