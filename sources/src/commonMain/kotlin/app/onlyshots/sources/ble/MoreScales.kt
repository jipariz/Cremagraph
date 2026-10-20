package app.onlyshots.sources.ble

/*
 * More scale protocols, ported from the MIT-licensed Zer0-bit/esp-arduino-ble-scales (ee17c40,
 * 2026-10-04). Byte layouts are cited per device. Several share the FFF0/FFF1/FFF2 or FFE0/FFE1
 * services, so the advertised name is what tells them apart.
 */

/**
 * DiFluid Microbalance (`Microbalance…`, `Mb…`). Frames are `DF DF func cmd len data… sum` on AA01 (service 00EE, or
 * 00DD on older units), sum = low byte of the sum of every byte before it. Sensor data (func 03, cmd 00, len ≥ 13)
 * starts with the weight×10 as a signed 32-bit BE. After connecting: unit to grams (`01 04 01 00`) and auto
 * notifications on (`01 00 01 01`); heartbeat "get device status" (`03 05 00`) every 2 s; tare `03 02 01 01`. All go to
 * AA01 with response.
 */
object DifluidProtocol : BleProtocol {
    override val id = "difluid"
    override val label = "DiFluid"
    private val CHAR = uuid16("aa01")

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName != null && (advertisedName.startsWith("Microbalance") || advertisedName.startsWith("Mb"))

    override fun routes(deviceName: String) =
        listOf(GattRoute(uuid16("00ee"), listOf(CHAR), CHAR), GattRoute(uuid16("00dd"), listOf(CHAR), CHAR))

    /** `DF DF func cmd len data… sum`. */
    fun frame(func: Int, cmd: Int, vararg data: Int): ByteArray {
        val f = bytes(0xDF, 0xDF, func, cmd, data.size, *data, 0)
        f[f.size - 1] = sumChecksum(f).toByte()
        return f
    }

    override fun onConnect(route: GattRoute) =
        listOf(
            BleWrite(frame(0x01, 0x04, 0x00), withResponse = true),
            BleWrite(frame(0x01, 0x00, 0x01), withResponse = true),
        )

    override val heartbeatMs: Long = 2_000

    override fun heartbeat(route: GattRoute) = listOf(BleWrite(frame(0x03, 0x05), withResponse = true))

    override fun tare(route: GattRoute) = listOf(BleWrite(frame(0x03, 0x02, 0x01), withResponse = true))

    override fun newDecoder(deviceName: String): BleDecoder {
        val buf = ByteBuffer()
        return BleDecoder { input ->
            buf.append(input)
            val out = ArrayList<BleReading>()
            while (true) {
                var start = 0
                while (start + 1 < buf.size && !(buf[start] == 0xDF && buf[start + 1] == 0xDF)) start++
                buf.drop(start)
                if (buf.size < 6) break
                val total = 6 + buf[4]
                if (buf.size < total) break
                if (sumChecksum(buf.peek(total)) != buf[total - 1]) {
                    buf.drop(1) // a false header or a corrupt frame; look for the next one
                    continue
                }
                val f = buf.take(total)
                if (f.u8(2) == 0x03 && f.u8(3) == 0x00 && f.u8(4) >= 13) {
                    val raw = (f.u8(5) shl 24) or (f.u8(6) shl 16) or (f.u8(7) shl 8) or f.u8(8)
                    out += BleReading(weightG = raw / 10f)
                }
            }
            out
        }
    }
}

/**
 * Eclair (`ECLAIR-…`). Every packet is `header data… xor`, xor = XOR of the data bytes (not the header). Data
 * characteristic AD73…: weight `57` + weight in mg as a signed 32-bit LE + 4 more bytes (10 bytes in all); flow `46`.
 * Config characteristic 4F9A…: battery `42 %`, timer `43 n`; commands go there too. Tare `54 01 01` with response,
 * heartbeat `43 00 00` every 2 s. One packet per notification.
 */
object EclairProtocol : BleProtocol {
    override val id = "eclair"
    override val label = "Eclair"
    private const val SERVICE = "b905eaea-2e63-0e04-7582-7913f10d8f81"
    private const val DATA = "ad736c5f-bbc9-1f96-d304-cb5d5f41e160"
    private const val CONFIG = "4f9a45ba-8e1b-4e07-e157-0814d393b968"
    const val WEIGHT = 0x57
    const val BATTERY = 0x42

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("ECLAIR-") == true

    override fun routes(deviceName: String) = listOf(GattRoute(SERVICE, listOf(DATA, CONFIG), CONFIG))

    /** `header data… xor(data)`. */
    fun packet(header: Int, vararg data: Int): ByteArray {
        var x = 0
        for (d in data) x = x xor d
        return bytes(header, *data, x and 0xFF)
    }

    override val heartbeatMs: Long = 2_000

    override fun heartbeat(route: GattRoute) = listOf(BleWrite(packet(0x43, 0x00)))

    override fun tare(route: GattRoute) = listOf(BleWrite(packet(0x54, 0x01), withResponse = true))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        if (f.size < 3 || xorChecksum(f.copyOfRange(1, f.size)) != f.u8(f.size - 1)) return@BleDecoder emptyList()
        when (f.u8(0)) {
            WEIGHT ->
                if (f.size >= 10) {
                    val raw = f.u8(1) or (f.u8(2) shl 8) or (f.u8(3) shl 16) or (f.u8(4) shl 24)
                    listOf(BleReading(weightG = raw / 1000f))
                } else emptyList()
            BATTERY -> listOf(BleReading(batteryPercent = f.u8(1)))
            else -> emptyList()
        }
    }
}

/**
 * iKape / IK-Cafe (A01 and rebrands, `IK-Cafe…`). 20-byte weight frames on FFF1: `A7 00 01 0E 13 …`, weight×10 as a
 * signed 16-bit BE at [9..10] (the scale floors negatives at 0), sum of [2..17] at [18], `7A` at [19]. Other frames
 * share the `A7 … 7A` envelope and are skipped. Grams are forced after connecting (`A7 00 01 02 04 05 0C 7A`); tare is
 * `A7 00 01 02 02 01 06 7A` with response. Commands go to FFF2.
 */
object IkapeProtocol : BleProtocol {
    override val id = "ikape"
    override val label = "iKape"
    private const val FRAME = 20

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("IK-Cafe") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("fff0"), listOf(uuid16("fff1")), uuid16("fff2")))

    override fun onConnect(route: GattRoute) = listOf(BleWrite(bytes(0xA7, 0x00, 0x01, 0x02, 0x04, 0x05, 0x0C, 0x7A)))

    override fun tare(route: GattRoute) =
        listOf(BleWrite(bytes(0xA7, 0x00, 0x01, 0x02, 0x02, 0x01, 0x06, 0x7A), withResponse = true))

    override fun newDecoder(deviceName: String): BleDecoder {
        val buf = ByteBuffer()
        return BleDecoder { input ->
            buf.append(input)
            val out = ArrayList<BleReading>()
            while (true) {
                var start = 0
                while (start < buf.size && buf[start] != 0xA7) start++
                buf.drop(start)
                if (buf.size < 4) break
                if (buf[3] != 0x0E) {
                    buf.drop(1) // not a weight frame
                    continue
                }
                if (buf.size < FRAME) break
                if (buf[FRAME - 1] != 0x7A) {
                    buf.drop(1)
                    continue
                }
                val f = buf.take(FRAME)
                if (sumChecksum(f, from = 2, until = 18) != f.u8(18)) continue
                out += BleReading(weightG = ((f.u8(9) shl 8) or f.u8(10)).toShort().toInt() / 10f)
            }
            out
        }
    }
}

/**
 * Insmart (names containing `863A`). 8-byte packets on FFF3: `12 06 status 00`, weight×10 as an unsigned 16-bit LE at
 * [4..5], `08 00`; status 05 = stable, 01 = changing. No checksum. Tare `50 00` to FFF1 with response. One packet per
 * notification.
 */
object InsmartProtocol : BleProtocol {
    override val id = "insmart"
    override val label = "Insmart"

    override fun matches(advertisedName: String?, serviceUuids: List<String>) = advertisedName?.contains("863A") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("fff0"), listOf(uuid16("fff3")), uuid16("fff1")))

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0x50, 0x00), withResponse = true))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        if (f.size < 6 || f.u8(0) != 0x12) return@BleDecoder emptyList()
        listOf(BleReading(weightG = (f.u8(4) or (f.u8(5) shl 8)) / 10f))
    }
}

/**
 * MyScale / Blackcoffee (`blackcoffee…`, `my_scale…`, `MY_SCALE…`). Packets of 15+ bytes on FFB2 (service FFB0; the
 * KP20488 variant uses FFE0/FFE1 for both directions): sign in the high nibble of [2] (8 or C = negative), weight in mg
 * as 28 bits BE in [3] low nibble..[6]. No checksum. Tare is `AC 40`, 16 zero bytes, `D2 D2` to FFB1. One packet per
 * notification.
 */
object MyScaleProtocol : BleProtocol {
    override val id = "myscale"
    override val label = "MyScale"
    private val prefixes = listOf("blackcoffee", "my_scale", "MY_SCALE")

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName != null && prefixes.any { advertisedName.startsWith(it) }

    override fun routes(deviceName: String) =
        listOf(
            GattRoute(uuid16("ffb0"), listOf(uuid16("ffb2")), uuid16("ffb1")),
            GattRoute(uuid16("ffe0"), listOf(uuid16("ffe1")), uuid16("ffe1")),
        )

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0xAC, 0x40) + ByteArray(16) + bytes(0xD2, 0xD2)))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        if (f.size < 15) return@BleDecoder emptyList()
        val raw = ((f.u8(3) and 0x0F) shl 24) or (f.u8(4) shl 16) or (f.u8(5) shl 8) or f.u8(6)
        val negative = (f.u8(2) shr 4) == 0x8 || (f.u8(2) shr 4) == 0xC
        listOf(BleReading(weightG = (if (negative) -raw else raw) / 1000f))
    }
}

/**
 * Precisa (`CFS-9002…`, `LSJ-001…`). 11-byte packets on FFF1: `AA 09 41 …`, sign at [6] (non-zero = negative),
 * weight×10 as a 16-bit LE at [7..8]. No checksum. Tare `AA 02 31 31` to FFF2. One packet per notification. Upstream
 * also claims nameless devices by a manufacturer-data heuristic (`FF FF … first MAC byte`); scanning here only sees
 * names and service UUIDs, so those are not matched.
 */
object PrecisaProtocol : BleProtocol {
    override val id = "precisa"
    override val label = "Precisa"
    private val prefixes = listOf("CFS-9002", "LSJ-001")

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName != null && prefixes.any { advertisedName.startsWith(it) }

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("fff0"), listOf(uuid16("fff1")), uuid16("fff2")))

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0xAA, 0x02, 0x31, 0x31)))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        if (f.size != 11 || f.u8(0) != 0xAA || f.u8(1) != 0x09 || f.u8(2) != 0x41) return@BleDecoder emptyList()
        val raw = ((f.u8(8) shl 8) or f.u8(7)).toShort().toInt()
        listOf(BleReading(weightG = (if (f.u8(6) > 0) -raw else raw) / 10f))
    }
}

/**
 * Tencent scales on a DSD TECH module (`DSD TECH…`). Packets of 18+ bytes on FFE1: `A7 00 24 0C 13 …`, sign in bit 4 of
 * [7], weight×10 as an unsigned 24-bit BE at [8..10]. Upstream does not check the trailing checksum, so neither do we.
 * Tare `54` to FFE1 with response. One packet per notification.
 */
object TencentProtocol : BleProtocol {
    override val id = "tencent"
    override val label = "Tencent"
    private val header = bytes(0xA7, 0x00, 0x24, 0x0C, 0x13)

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("DSD TECH") == true

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("ffe0"), listOf(uuid16("ffe1")), uuid16("ffe1")))

    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0x54), withResponse = true))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        if (f.size < 18 || !f.copyOfRange(0, 5).contentEquals(header)) return@BleDecoder emptyList()
        val v = ((f.u8(8) shl 16) or (f.u8(9) shl 8) or f.u8(10)) / 10f
        listOf(BleReading(weightG = if ((f.u8(7) shr 4) and 1 != 0) -v else v))
    }
}

/**
 * Varia AKU (Mini, Pro, Micro: `AKU MINI SCALE…`, `VARIA AKU…`, `Varia AKU…`). Frames are `FA type len payload xor` on
 * FFF1, xor = XOR of type, len and payload. Weight `01`: sign in bit 4 of payload[0], weight×100 in its low nibble and
 * the next two bytes (BE). Battery `85`: percent. Timer `87` and timer events `88`–`8A` are skipped. Tare is `FA 82 01
 * 01 82` to FFF2.
 */
object VariaProtocol : BleProtocol {
    override val id = "varia"
    override val label = "Varia"
    private val prefixes = listOf("AKU MINI SCALE", "VARIA AKU", "Varia AKU")

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName != null && prefixes.any { advertisedName.startsWith(it) }

    override fun routes(deviceName: String) = listOf(GattRoute(uuid16("fff0"), listOf(uuid16("fff1")), uuid16("fff2")))

    /** `FA type len payload xor`. */
    fun frame(type: Int, vararg payload: Int): ByteArray {
        val f = bytes(0xFA, type, payload.size, *payload, 0)
        f[f.size - 1] = xorChecksum(f.copyOfRange(1, f.size)).toByte()
        return f
    }

    override fun tare(route: GattRoute) = listOf(BleWrite(frame(0x82, 0x01)))

    override fun newDecoder(deviceName: String): BleDecoder {
        val buf = ByteBuffer()
        return BleDecoder { input ->
            buf.append(input)
            val out = ArrayList<BleReading>()
            while (true) {
                var start = 0
                while (start < buf.size && buf[start] != 0xFA) start++
                buf.drop(start)
                if (buf.size < 4) break
                val total = 4 + buf[2]
                if (buf.size < total) break
                val f = buf.peek(total)
                if (xorChecksum(f.copyOfRange(1, total)) != f.u8(total - 1)) {
                    buf.drop(1) // a false header or a corrupt frame; look for the next one
                    continue
                }
                buf.drop(total)
                when (f.u8(1)) {
                    0x01 ->
                        if (f.u8(2) == 3) {
                            val v = (((f.u8(3) and 0x0F) shl 16) or (f.u8(4) shl 8) or f.u8(5)) / 100f
                            out += BleReading(weightG = if (f.u8(3) and 0x10 != 0) -v else v)
                        }
                    0x85 -> if (f.u8(2) == 1) out += BleReading(batteryPercent = f.u8(3))
                }
            }
            out
        }
    }
}

/**
 * WeighMyBru (DIY, `WeighMyBru…`): Bookoo's frame on the Nordic UART service. 20-byte `03 0B` frames, weight sign at
 * [6] (`-` = negative), weight×100 as 24-bit BE at [7..9], XOR checksum last. Upstream calls 6E400002 the weight and
 * 6E400003 the command characteristic, subscribes to whichever notifies and writes to 6E400003; so do we. It also sends
 * the notification request and heartbeat of the Acaia code it was templated on, which are kept as sent. Tare is `03 0A
 * 01 01 00 09`.
 */
object WeighMyBruProtocol : BleProtocol {
    override val id = "weighmybru"
    override val label = "WeighMyBru"
    private const val FRAME = 20
    private const val NUS = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    private const val WEIGHT = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    private const val COMMAND = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("WeighMyBru") == true

    override fun routes(deviceName: String) = listOf(GattRoute(NUS, listOf(WEIGHT, COMMAND), COMMAND))

    /** As upstream's sendMessage: the last byte is replaced by the XOR of the others. */
    private fun message(vararg v: Int) = bytes(*v).also { it[it.size - 1] = xorChecksum(it).toByte() }

    private val notificationRequest = message(0x07, 0, 0, 0, 0, 0, 0)

    override fun onConnect(route: GattRoute) = listOf(BleWrite(notificationRequest))

    override val heartbeatMs: Long = 2_000

    override fun heartbeat(route: GattRoute) =
        listOf(BleWrite(message(0x02, 0x00)), BleWrite(notificationRequest), BleWrite(message(0x00)))

    override fun tare(route: GattRoute) = listOf(BleWrite(message(0x03, 0x0A, 0x01, 0x01, 0x00, 0x00)))

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
                if (buf[1] != 0x0B || xorChecksum(buf.peek(FRAME)) != buf[FRAME - 1]) {
                    buf.drop(1)
                    continue
                }
                val f = buf.take(FRAME)
                val weight = ((f.u8(7) shl 16) or (f.u8(8) shl 8) or f.u8(9)) / 100f
                out += BleReading(weightG = if (f.u8(6) == 0x2D) -weight else weight)
            }
            out
        }
    }
}

/**
 * Wilfa Svart Uni (WSS-2, `Wilfa…`; protocol notes by ts95, linked from upstream). Packets start with `FA kind`; weight
 * is `FA 01` + weight×10 as a signed 16-bit BE, always in grams. Gen 1 (AA batteries) sends on FFB1 and FFB2 and takes
 * commands on FEC7 in its own FEE7 service; Gen 2 (USB-C) does everything on FFB1. Tare `F5 10`. No checksum; one
 * packet per notification.
 */
object WilfaProtocol : BleProtocol {
    override val id = "wilfa"
    override val label = "Wilfa"

    override fun matches(advertisedName: String?, serviceUuids: List<String>) =
        advertisedName?.startsWith("Wilfa") == true

    override fun routes(deviceName: String) =
        listOf(
            GattRoute(uuid16("ffb0"), listOf(uuid16("ffb1"), uuid16("ffb2")), uuid16("fec7"), uuid16("fee7")),
            GattRoute(uuid16("ffb0"), listOf(uuid16("ffb1")), uuid16("ffb1")),
        )

    // Upstream uses whichever write type the characteristic supports; KableBleDeviceLink falls back the same way.
    override fun tare(route: GattRoute) = listOf(BleWrite(bytes(0xF5, 0x10), withResponse = true))

    override fun newDecoder(deviceName: String) = BleDecoder { f ->
        if (f.size < 4 || f.u8(0) != 0xFA || f.u8(1) != 0x01) return@BleDecoder emptyList()
        listOf(BleReading(weightG = ((f.u8(2) shl 8) or f.u8(3)).toShort().toInt() / 10f))
    }
}
