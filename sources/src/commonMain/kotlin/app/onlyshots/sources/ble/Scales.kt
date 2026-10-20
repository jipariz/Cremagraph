package app.onlyshots.sources.ble

/*
 * Scale protocols, ported from the MIT-licensed Zer0-bit/esp-arduino-ble-scales (ee17c40,
 * 2026-10-04; originally kstam) and Bookoo's published protocols (BooKooCode/OpenSource, MIT).
 * Acaia Umbra and the Decent Scale heartbeat come from the gaggimate fork (180070f, 2026-06-08),
 * which Zer0-bit does not have. Byte layouts are cited per device; MoreScales.kt has the rest.
 */

/**
 * Acaia (Lunar, Pearl, Pyxis, Proch, Umbra). Frames are `EF DD type payload ck1 ck2` where payload[0] is the payload
 * length, ck1/ck2 are the sums of the even/odd payload bytes. Weight events (type 0x0C, event 0x05) carry the value, a
 * decimal scaling and a sign flag; Umbra puts the value in bytes 2–3 big-endian instead of 0–1 little-endian. The scale
 * streams only after an ident and a notification request, and needs a heartbeat every 2 s. Umbra (name prefix, fe40
 * route, big-endian weight) is from the gaggimate fork.
 */
object AcaiaProtocol : BleProtocol {
    override val id = "acaia"
    override val label = "Acaia"
    private val prefixes = listOf("ACAIA", "PYXIS", "LUNAR", "PEARL", "PROCH", "UMBRA")

    const val TYPE_SYSTEM = 0x00
    const val TYPE_TARE = 0x04
    const val TYPE_HANDSHAKE = 0x06
    const val TYPE_INFO = 0x07
    const val TYPE_STATUS = 0x08
    const val TYPE_IDENTIFY = 0x0B
    const val TYPE_EVENT = 0x0C
    private const val EVENT_WEIGHT = 0x05

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName != null && prefixes.any { advertisedName.uppercase().startsWith(it) }

    override fun routes(deviceName: String) =
        listOf(
            GattRoute(uuid16("1820"), listOf(uuid16("2a80")), uuid16("2a80")),
            GattRoute(
                "49535343-fe7d-4ae5-8fa9-9fafd205e455",
                listOf("49535343-1e4d-4bd9-ba61-23c647249616", "49535343-8841-43f4-a8d4-ecbe34729bb3"),
                "49535343-8841-43f4-a8d4-ecbe34729bb3",
            ),
            GattRoute(
                "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
                listOf("0000fe42-8e22-4541-9d4c-21edae82ed19", "0000fe41-8e22-4541-9d4c-21edae82ed19"),
                "0000fe41-8e22-4541-9d4c-21edae82ed19",
            ),
        )

    fun frame(type: Int, payload: ByteArray): ByteArray {
        var ck1 = 0
        var ck2 = 0
        payload.forEachIndexed { i, b -> if (i % 2 == 0) ck1 += b.toInt() and 0xFF else ck2 += b.toInt() and 0xFF }
        return bytes(0xEF, 0xDD, type) + payload + bytes(ck1 and 0xFF, ck2 and 0xFF)
    }

    private fun event(payload: ByteArray) = frame(TYPE_EVENT, bytes(payload.size + 1) + payload)

    private val ident = frame(TYPE_IDENTIFY, ByteArray(15) { 0x2D })
    private val notificationRequest = event(bytes(0, 1, 1, 2, 2, 5, 3, 4))

    override fun onConnect(route: GattRoute) = listOf(BleWrite(ident), BleWrite(notificationRequest))

    override val heartbeatMs: Long = 2_000

    override fun heartbeat(route: GattRoute) =
        listOf(
            BleWrite(frame(TYPE_SYSTEM, bytes(0x02, 0x00))),
            BleWrite(notificationRequest),
            BleWrite(frame(TYPE_HANDSHAKE, bytes(0x00))),
        )

    override fun tare(route: GattRoute) = listOf(BleWrite(frame(TYPE_TARE, bytes(0x00))))

    override fun newDecoder(deviceName: String): BleDecoder {
        val umbra = "UMBRA" in deviceName.uppercase()
        val buf = ByteBuffer()
        return BleDecoder { input ->
            buf.append(input)
            val out = ArrayList<BleReading>()
            while (true) {
                // Resync on the EF DD header.
                var start = 0
                while (start + 1 < buf.size && !(buf[start] == 0xEF && buf[start + 1] == 0xDD)) start++
                buf.drop(start)
                if (buf.size < 6) break
                val total = 3 + buf[3] + 2
                if (buf.size < total) break
                val f = buf.take(total)
                val payload = f.copyOfRange(3, total - 2)
                var ck1 = 0
                var ck2 = 0
                payload.forEachIndexed { i, b ->
                    if (i % 2 == 0) ck1 += b.toInt() and 0xFF else ck2 += b.toInt() and 0xFF
                }
                if ((ck1 and 0xFF) != f.u8(total - 2) || (ck2 and 0xFF) != f.u8(total - 1)) continue
                when (f.u8(2)) {
                    TYPE_EVENT ->
                        if (payload.size >= 8 && payload.u8(1) == EVENT_WEIGHT) {
                            decodeWeight(payload, 2, umbra)?.let { out += BleReading(weightG = it) }
                        }
                    TYPE_STATUS -> if (payload.size >= 2) out += BleReading(batteryPercent = payload.u8(1) and 0x7F)
                }
            }
            out
        }
    }

    internal fun decodeWeight(p: ByteArray, o: Int, umbra: Boolean): Float? {
        val raw = if (umbra) (p.u8(o + 2) shl 8) or p.u8(o + 3) else (p.u8(o + 1) shl 8) or p.u8(o)
        val divisor =
            when (p.u8(o + 4)) {
                1 -> 10f
                2 -> 100f
                3 -> 1000f
                4 -> 10000f
                else -> return null
            }
        val v = raw / divisor
        return if (p.u8(o + 5) and 0x02 != 0) -v else v
    }
}

/**
 * Bookoo Mini / Ultra scale (BooKooCode/OpenSource bookoo_mini_scale/protocols.md). 20-byte frames on FF11: `03 0B`,
 * timer ms (3 bytes BE), unit, weight sign + weight×100 (3 bytes BE), flow sign + flow×100 (2 bytes BE), battery %, …,
 * XOR checksum. Commands go to FF12.
 */
object BookooScaleProtocol : BleProtocol {
    override val id = "bookoo"
    override val label = "Bookoo"
    private const val FRAME = 20

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("BOOKOO_SC") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("0ffe"), listOf(uuid16("ff11")), uuid16("ff12")))

    /** `03 0A cmd 00 00 xor`: 0x01 tare, 0x04 start, 0x05 stop and 0x06 reset the timer, 0x07 tare + start timer. */
    fun command(cmd: Int): ByteArray =
        bytes(0x03, 0x0A, cmd, 0x00, 0x00, 0x00).also { it[5] = xorChecksum(it).toByte() }

    /**
     * A clean slate for the next shot: the scale's own timer stopped and back at 0:00, then a plain tare. Not 0x07,
     * which starts the timer the moment the record screen opens, long before the shot.
     */
    override fun tare(route: GattRoute) =
        listOf(BleWrite(command(STOP_TIMER)), BleWrite(command(RESET_TIMER)), BleWrite(command(TARE)))

    private const val TARE = 0x01
    private const val STOP_TIMER = 0x05
    private const val RESET_TIMER = 0x06

    override fun newDecoder(deviceName: String): BleDecoder {
        val buf = ByteBuffer()
        return BleDecoder { input ->
            buf.append(input)
            val out = ArrayList<BleReading>()
            while (true) {
                var start = 0
                while (start < buf.size && buf[start] != 0x03) start++
                buf.drop(start)
                if (buf.size < FRAME) break
                if (buf[1] != 0x0B) {
                    buf.drop(if (buf[1] == 0x0A) FRAME else 1)
                    continue
                }
                val f = buf.take(FRAME)
                if (xorChecksum(f) != f.u8(FRAME - 1)) continue
                val weight = ((f.u8(7) shl 16) or (f.u8(8) shl 8) or f.u8(9)) / 100f
                val flow = ((f.u8(11) shl 8) or f.u8(12)) / 100f
                out +=
                    BleReading(
                        weightG = if (f.u8(6) == 0x2D) -weight else weight,
                        weightFlowGps = if (f.u8(10) == 0x2D) -flow else flow,
                        batteryPercent = f.u8(13),
                        timerMs = ((f.u8(2) shl 16) or (f.u8(3) shl 8) or f.u8(4)).toLong(),
                    )
            }
            out
        }
    }
}

/**
 * Decent Scale. 7- or 10-byte frames on FFF4: `03 CA|CE`, weight×10 as a signed 16-bit BE, …, XOR checksum (0 means
 * "not set"). Commands go to 36F5. The display-on command (`03 0A 01 01 00 01 08`, byte 5 = keep heartbeat) and the 5 s
 * heartbeat are from the gaggimate fork; Zer0-bit sends neither and tares with `03 0F 00 00 00 00 0C`.
 */
object DecentScaleProtocol : BleProtocol {
    override val id = "decent-scale"
    override val label = "Decent Scale"

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("Decent Scale") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("fff0"), listOf(uuid16("fff4")), uuid16("36f5")))

    override fun onConnect(route: GattRoute) = listOf(BleWrite(bytes(0x03, 0x0A, 0x01, 0x01, 0x00, 0x01, 0x08)))

    override val heartbeatMs: Long = 5_000

    override fun heartbeat(route: GattRoute) = listOf(BleWrite(bytes(0x03, 0x0A, 0x03, 0xFF, 0xFF, 0x00, 0x0A)))

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0x03, 0x0F, 0x00, 0x00, 0x00, 0x01, 0x0D)))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        // Each notification is one whole frame.
        if ((f.size != 7 && f.size != 10) || f.u8(0) != 0x03 || (f.u8(1) != 0xCA && f.u8(1) != 0xCE))
            return@BleDecoder emptyList()
        val ck = f.u8(f.size - 1)
        if (ck != 0 && ck != xorChecksum(f)) return@BleDecoder emptyList()
        val raw = ((f.u8(2) shl 8) or f.u8(3)).toShort().toInt()
        listOf(BleReading(weightG = raw / 10f))
    }
}

/**
 * EspressiScale, a Decent Scale clone with a stricter stream: only 7-byte `03 CE` frames on FFF4, weight×10 as a signed
 * 16-bit BE at [2..3], XOR checksum always checked. Tare `03 0F 00 00 00 00 0C` to 36F5; no heartbeat.
 */
object EspressiScaleProtocol : BleProtocol {
    override val id = "espressiscale"
    override val label = "EspressiScale"

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("EspressiScale") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("fff0"), listOf(uuid16("fff4")), uuid16("36f5")))

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0x03, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x0C)))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        // Each notification is one whole frame.
        if (f.size != 7 || f.u8(0) != 0x03 || f.u8(1) != 0xCE || xorChecksum(f) != f.u8(6))
            return@BleDecoder emptyList()
        listOf(BleReading(weightG = ((f.u8(2) shl 8) or f.u8(3)).toShort().toInt() / 10f))
    }
}

/** Felicita (Arc, Incline, Parallel): ASCII frames on FFE1, sign at [2], six digits [3..8] = weight×100. */
object FelicitaProtocol : BleProtocol {
    override val id = "felicita"
    override val label = "Felicita"

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.uppercase()?.startsWith("FELICITA") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("ffe0"), listOf(uuid16("ffe1")), uuid16("ffe1")))

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0x54), withResponse = true))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        if (f.size < 18) return@BleDecoder emptyList()
        var v = 0
        for (i in 3..8) {
            val c = f.u8(i)
            if (c !in '0'.code..'9'.code) return@BleDecoder emptyList()
            v = v * 10 + (c - '0'.code)
        }
        listOf(BleReading(weightG = (if (f.u8(2) == '-'.code) -v else v) / 100f))
    }
}

/**
 * Timemore (Black Mirror, older protocol): 9-byte frames on 2A9D (indications): `10`, dripper weight, scale weight,
 * both ×10 as 32-bit LE. `02 00` to the weight characteristic asks for the stream, then a heartbeat `00` there every 2
 * s; tare `00` to the command characteristic.
 */
object TimemoreProtocol : BleProtocol {
    override val id = "timemore"
    override val label = "Timemore"
    private const val COMMAND = "553f4e49-bf21-4468-9c6c-0e4fb5b17697"
    private val WEIGHT = uuid16("2a9d")

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("Timemore Scale") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("181d"), listOf(WEIGHT), COMMAND))

    override fun onConnect(route: GattRoute) =
        listOf(BleWrite(bytes(0x02, 0x00), characteristic = WEIGHT, withResponse = true))

    override val heartbeatMs: Long = 2_000

    override fun heartbeat(route: GattRoute) =
        listOf(BleWrite(bytes(0x00), characteristic = WEIGHT, withResponse = true))

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0x00), withResponse = true))

    override fun newDecoder(deviceName: String): BleDecoder {
        val buf = ByteBuffer()
        return BleDecoder { input ->
            buf.append(input)
            val out = ArrayList<BleReading>()
            while (buf.size >= 9) {
                val f = buf.take(9)
                if (f.u8(0) != 0x10) continue
                val raw = f.u8(5) or (f.u8(6) shl 8) or (f.u8(7) shl 16) or (f.u8(8) shl 24)
                out += BleReading(weightG = raw / 10f)
            }
            out
        }
    }
}

/**
 * Timemore Dot and other "Ble2025" Timemore scales, the newer protocol. Frames are `A5 5A type sub len(2, BE) payload
 * crc(2, LE)` on FFF1; weight data is type `01 01` with the weight×10 as a signed 32-bit BE at payload[0..3]. The app
 * wakes the stream with six queries `A5 5A 02 q 00 00 00 00` (q = 19, 8, 5, 2, 6, 12; sent with a zero CRC, as upstream
 * does) on FFF2. Tare is `A5 5A 03 0D 00 00` + CRC-16/ARC (init 0, poly 0xA001, LSB first). The scale wants a bond,
 * which the OS sets up on first connect. Upstream does not check the CRC of incoming frames, so neither do we.
 */
object TimemoreDotProtocol : BleProtocol {
    override val id = "timemore-dot"
    override val label = "Timemore Dot"

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.contains("TIMEMORE_Dot") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("fff0"), listOf(uuid16("fff1")), uuid16("fff2")))

    private val wakeQueries = listOf(19, 8, 5, 2, 6, 12)
    private const val MAX_PAYLOAD = 64

    override fun onConnect(route: GattRoute) = wakeQueries.map {
        BleWrite(bytes(0xA5, 0x5A, 0x02, it, 0, 0, 0, 0), withResponse = true)
    }

    override fun tare(route: GattRoute): List<BleWrite> {
        val f = bytes(0xA5, 0x5A, 0x03, 0x0D, 0, 0, 0, 0)
        val crc = crc16Arc(f, 6)
        f[6] = (crc and 0xFF).toByte()
        f[7] = (crc shr 8).toByte()
        return listOf(BleWrite(f))
    }

    internal fun crc16Arc(data: ByteArray, until: Int): Int {
        var crc = 0
        for (i in 0 until until) {
            crc = crc xor data.u8(i)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }

    override fun newDecoder(deviceName: String): BleDecoder {
        val buf = ByteBuffer()
        return BleDecoder { input ->
            buf.append(input)
            val out = ArrayList<BleReading>()
            while (true) {
                var start = 0
                while (start + 1 < buf.size && !(buf[start] == 0xA5 && buf[start + 1] == 0x5A)) start++
                buf.drop(start)
                if (buf.size < 6) break
                val len = (buf[4] shl 8) or buf[5]
                if (len > MAX_PAYLOAD) {
                    buf.drop(2) // a false header; look for the next one
                    continue
                }
                val total = 6 + len + 2
                if (buf.size < total) break
                val f = buf.take(total)
                if (f.u8(2) == 0x01 && f.u8(3) == 0x01 && total - 8 >= 9) {
                    val raw = (f.u8(6) shl 24) or (f.u8(7) shl 16) or (f.u8(8) shl 8) or f.u8(9)
                    out += BleReading(weightG = raw / 10f)
                }
            }
            out
        }
    }
}
