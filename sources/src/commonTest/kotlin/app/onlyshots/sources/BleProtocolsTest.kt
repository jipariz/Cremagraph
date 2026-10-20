package app.onlyshots.sources

import app.onlyshots.sources.ble.AcaiaProtocol
import app.onlyshots.sources.ble.BleProtocols
import app.onlyshots.sources.ble.BookooScaleProtocol
import app.onlyshots.sources.ble.DecentScaleProtocol
import app.onlyshots.sources.ble.DifluidProtocol
import app.onlyshots.sources.ble.EclairProtocol
import app.onlyshots.sources.ble.EspressiScaleProtocol
import app.onlyshots.sources.ble.FelicitaProtocol
import app.onlyshots.sources.ble.IkapeProtocol
import app.onlyshots.sources.ble.InsmartProtocol
import app.onlyshots.sources.ble.MyScaleProtocol
import app.onlyshots.sources.ble.PrecisaProtocol
import app.onlyshots.sources.ble.TencentProtocol
import app.onlyshots.sources.ble.TimemoreDotProtocol
import app.onlyshots.sources.ble.TimemoreProtocol
import app.onlyshots.sources.ble.VariaProtocol
import app.onlyshots.sources.ble.WeighMyBruProtocol
import app.onlyshots.sources.ble.WilfaProtocol
import app.onlyshots.sources.ble.uuid16
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

private fun hex(s: String) = s.split(" ").map { it.toInt(16) }.toIntArray().let { b(*it) }

class BleProtocolsTest {
    @Test
    fun matchesDevicesByAdvertisedName() {
        assertEquals(AcaiaProtocol, BleProtocols.find("LUNAR-2A1B3C"))
        assertEquals(AcaiaProtocol, BleProtocols.find("PEARLS-123"))
        assertEquals(AcaiaProtocol, BleProtocols.find("UMBRA"))
        assertEquals(BookooScaleProtocol, BleProtocols.find("BOOKOO_SC 1234"))
        assertEquals(DecentScaleProtocol, BleProtocols.find("Decent Scale"))
        assertEquals(EspressiScaleProtocol, BleProtocols.find("EspressiScale"))
        assertEquals(FelicitaProtocol, BleProtocols.find("FELICITA ARC"))
        assertEquals(TimemoreProtocol, BleProtocols.find("Timemore Scale"))
        assertNull(BleProtocols.find("JBL Flip 5"))
        assertNull(BleProtocols.find(null))
    }

    private val sampleNames =
        mapOf(
            "LUNAR-2A1B3C" to AcaiaProtocol,
            "BOOKOO_SC 1234" to BookooScaleProtocol,
            "Decent Scale" to DecentScaleProtocol,
            "EspressiScale" to EspressiScaleProtocol,
            "FELICITA ARC" to FelicitaProtocol,
            "Timemore Scale" to TimemoreProtocol,
            "TIMEMORE_Dot" to TimemoreDotProtocol,
            " TIMEMORE_Dot 01" to TimemoreDotProtocol,
            "Microbalance Ti" to DifluidProtocol,
            "Mb-1A2B" to DifluidProtocol,
            "ECLAIR-0042" to EclairProtocol,
            "IK-Cafe A01" to IkapeProtocol,
            "SCALE863A" to InsmartProtocol,
            "blackcoffee" to MyScaleProtocol,
            "my_scale_2" to MyScaleProtocol,
            "MY_SCALE" to MyScaleProtocol,
            "CFS-9002" to PrecisaProtocol,
            "LSJ-001 B" to PrecisaProtocol,
            "DSD TECH" to TencentProtocol,
            "AKU MINI SCALE" to VariaProtocol,
            "VARIA AKU" to VariaProtocol,
            "Varia AKU-PRO" to VariaProtocol,
            "Varia AKU-MICRO" to VariaProtocol,
            "WeighMyBru-01" to WeighMyBruProtocol,
            "Wilfa Svart scale" to WilfaProtocol,
        )

    @Test
    fun everyScaleMatchesExactlyOneProtocol() {
        for ((name, protocol) in sampleNames) {
            assertEquals(protocol, BleProtocols.find(name), name)
            assertEquals(listOf(protocol), BleProtocols.all.filter { it.matches(name) }, name)
        }
    }

    @Test
    fun insmartsContainsMatchComesAfterEveryPrefix() {
        // Insmart claims "863A" anywhere in the name, so a scale whose serial happens to contain it keeps its protocol.
        assertEquals(InsmartProtocol, BleProtocols.all.last())
        assertEquals(AcaiaProtocol, BleProtocols.find("LUNAR-863A12"))
        assertNull(BleProtocols.find("MB Speaker"), "the DiFluid prefix is case-sensitive")
        assertNull(BleProtocols.find("timemore_dot"))
    }

    @Test
    fun shortUuids() {
        assertEquals("0000fff0-0000-1000-8000-00805f9b34fb", uuid16("FFF0"))
        assertEquals("000036f5-0000-1000-8000-00805f9b34fb", uuid16("36F5"))
    }

    // Acaia ----------------------------------------------------------------------------------

    private fun acaiaWeight(raw: Int, scaling: Int, negative: Boolean = false) =
        AcaiaProtocol.frame(
            AcaiaProtocol.TYPE_EVENT,
            b(8, 0x05, raw and 0xFF, raw shr 8, 0, 0, scaling, if (negative) 0x02 else 0),
        )

    @Test
    fun acaiaCommandsMatchTheReferenceBytes() {
        val r = AcaiaProtocol.routes("LUNAR")[1]
        val (ident, notify) = AcaiaProtocol.onConnect(r).map { it.bytes }
        assertContentEquals(hex("ef dd 0b 2d 2d 2d 2d 2d 2d 2d 2d 2d 2d 2d 2d 2d 2d 2d 68 3b"), ident)
        assertContentEquals(hex("ef dd 0c 09 00 01 01 02 02 05 03 04 15 06"), notify)
        assertContentEquals(hex("ef dd 04 00 00 00"), AcaiaProtocol.tare(r).single().bytes)
        assertEquals(2_000L, AcaiaProtocol.heartbeatMs)
    }

    @Test
    fun acaiaWeightAcrossSplitNotificationsAndJunk() {
        val d = AcaiaProtocol.newDecoder("LUNAR-1")
        val frame = acaiaWeight(365, scaling = 1)
        assertTrue(d.feed(b(0x00, 0x13) + frame.copyOfRange(0, 5)).isEmpty())
        assertEquals(listOf(36.5f), d.feed(frame.copyOfRange(5, frame.size)).map { it.weightG })
        val two = acaiaWeight(1234, 2) + acaiaWeight(50, 1, negative = true)
        assertEquals(listOf(12.34f, -5f), d.feed(two).map { it.weightG })
    }

    @Test
    fun acaiaDropsBadChecksumsAndReadsBattery() {
        val d = AcaiaProtocol.newDecoder("PEARL")
        val bad = acaiaWeight(365, 1).also { it[it.size - 1] = 0 }
        assertTrue(d.feed(bad).isEmpty())
        assertEquals(listOf(36.5f), d.feed(acaiaWeight(365, 1)).map { it.weightG })
        val status = AcaiaProtocol.frame(AcaiaProtocol.TYPE_STATUS, b(4, 0x80 or 77, 2, 0))
        assertEquals(77, d.feed(status).single().batteryPercent)
    }

    @Test
    fun acaiaUmbraKeepsWeightInBigEndianBytes() {
        val d = AcaiaProtocol.newDecoder("UMBRA-77")
        val f = AcaiaProtocol.frame(AcaiaProtocol.TYPE_EVENT, b(8, 0x05, 0, 0, 0x01, 0x6D, 1, 0))
        assertEquals(listOf(36.5f), d.feed(f).map { it.weightG })
    }

    // Bookoo ----------------------------------------------------------------------------------

    private fun bookooFrame(
        weight100: Int,
        flow100: Int,
        battery: Int,
        negative: Boolean = false,
        timerMs: Int = 12_345,
    ): ByteArray {
        val f =
            b(
                0x03,
                0x0B,
                (timerMs shr 16) and 0xFF,
                (timerMs shr 8) and 0xFF,
                timerMs and 0xFF,
                0x02,
                if (negative) 0x2D else 0x2B,
                (weight100 shr 16) and 0xFF,
                (weight100 shr 8) and 0xFF,
                weight100 and 0xFF,
                0x2B,
                (flow100 shr 8) and 0xFF,
                flow100 and 0xFF,
                battery,
                0,
                0,
                0,
                0,
                0,
                0,
            )
        var x = 0
        for (i in 0 until 19) x = x xor (f[i].toInt() and 0xFF)
        f[19] = x.toByte()
        return f
    }

    @Test
    fun bookooWeightFlowBatteryAndTimer() {
        val d = BookooScaleProtocol.newDecoder("BOOKOO_SC")
        val r = d.feed(bookooFrame(3650, 185, 88)).single()
        assertEquals(36.5f, r.weightG)
        assertEquals(1.85f, r.weightFlowGps)
        assertEquals(88, r.batteryPercent)
        assertEquals(12_345L, r.timerMs)
        assertEquals(-0.3f, d.feed(bookooFrame(30, 0, 88, negative = true)).single().weightG)
    }

    @Test
    fun bookooReassemblesAndRejectsCorruptFrames() {
        val d = BookooScaleProtocol.newDecoder("BOOKOO_SC")
        val f = bookooFrame(1000, 0, 50)
        assertTrue(d.feed(f.copyOfRange(0, 12)).isEmpty())
        assertEquals(listOf(10f), d.feed(f.copyOfRange(12, 20)).map { it.weightG })
        assertTrue(d.feed(bookooFrame(1000, 0, 50).also { it[8] = 9 }).isEmpty())
    }

    @Test
    fun bookooTareStopsAndResetsItsTimerAndNeverStartsIt() {
        val writes = BookooScaleProtocol.tare(BookooScaleProtocol.routes("")[0]).map { it.bytes.toList() }
        // Stop timer, reset timer, tare, each with its XOR checksum; never 0x07 (tare + start timer).
        assertEquals(
            listOf(hex("03 0a 05 00 00 0c"), hex("03 0a 06 00 00 0f"), hex("03 0a 01 00 00 08")).map { it.toList() },
            writes,
        )
    }

    // Decent Scale ----------------------------------------------------------------------------

    @Test
    fun decentScaleWeights() {
        val d = DecentScaleProtocol.newDecoder("Decent Scale")
        // 36.5 g = 365 = 0x016D, checksum = XOR of the first six bytes.
        val f =
            b(0x03, 0xCE, 0x01, 0x6D, 0x00, 0x00, 0x00).also {
                var x = 0
                for (i in 0..5) x = x xor (it[i].toInt() and 0xFF)
                it[6] = x.toByte()
            }
        assertEquals(listOf(36.5f), d.feed(f).map { it.weightG })
        assertEquals(
            listOf(-1.2f),
            d.feed(b(0x03, 0xCA, 0xFF, 0xF4, 0, 0, 0)).map { it.weightG },
        ) // checksum 0 = unchecked
        assertTrue(d.feed(b(0x03, 0xCE, 0x01, 0x6D, 0, 0, 0x55)).isEmpty())
        assertTrue(d.feed(b(0x03, 0x0A, 0, 0, 0, 0, 0)).isEmpty())
    }

    @Test
    fun decentScaleCommands() {
        val r = DecentScaleProtocol.routes("")[0]
        assertContentEquals(hex("03 0f 00 00 00 01 0d"), DecentScaleProtocol.tare(r).single().bytes)
        assertContentEquals(hex("03 0a 03 ff ff 00 0a"), DecentScaleProtocol.heartbeat(r).single().bytes)
        assertEquals(5_000L, DecentScaleProtocol.heartbeatMs)
    }

    // EspressiScale ---------------------------------------------------------------------------

    private fun espressiFrame(weight10: Int) =
        b(0x03, 0xCE, (weight10 shr 8) and 0xFF, weight10 and 0xFF, 0, 0, 0).also {
            var x = 0
            for (i in 0..5) x = x xor (it[i].toInt() and 0xFF)
            it[6] = x.toByte()
        }

    @Test
    fun espressiScaleWeightsAndStrictChecksum() {
        val d = EspressiScaleProtocol.newDecoder("EspressiScale")
        assertEquals(listOf(36.5f), d.feed(espressiFrame(365)).map { it.weightG })
        assertEquals(listOf(-1.2f), d.feed(espressiFrame(-12)).map { it.weightG })
        assertTrue(d.feed(espressiFrame(365).also { it[6] = 0x55 }).isEmpty())
        assertTrue(d.feed(b(0x03, 0xCE, 0x01, 0x6D, 0, 0, 0)).isEmpty(), "a zero checksum is not a pass")
        assertTrue(d.feed(b(0x03, 0xCA, 0x01, 0x6D, 0, 0, 0xA5)).isEmpty(), "only CE frames")
        val r = EspressiScaleProtocol.routes("")[0]
        assertContentEquals(hex("03 0f 00 00 00 00 0c"), EspressiScaleProtocol.tare(r).single().bytes)
        assertNull(EspressiScaleProtocol.heartbeatMs)
    }

    // Felicita, Timemore -----------------------------------------------------------------

    @Test
    fun felicitaAsciiDigits() {
        val d = FelicitaProtocol.newDecoder("FELICITA")
        val f = ByteArray(18).also { "01+003650".encodeToByteArray().copyInto(it) }
        assertEquals(listOf(36.5f), d.feed(f).map { it.weightG })
        val neg = ByteArray(18).also { "01-000120".encodeToByteArray().copyInto(it) }
        assertEquals(listOf(-1.2f), d.feed(neg).map { it.weightG })
        assertTrue(d.feed(ByteArray(18) { 'x'.code.toByte() }).isEmpty())
        assertTrue(FelicitaProtocol.tare(FelicitaProtocol.routes("")[0]).single().withResponse)
    }

    @Test
    fun timemoreScaleWeight() {
        val d = TimemoreProtocol.newDecoder("Timemore Scale")
        // From the library's comment: 10 78 08 00 00 78 08 00 00 = 216.8 g
        assertEquals(listOf(216.8f), d.feed(hex("10 78 08 00 00 78 08 00 00")).map { it.weightG })
        val r = TimemoreProtocol.routes("")[0]
        assertEquals(
            uuid16("2a9d"),
            TimemoreProtocol.heartbeat(r).single().characteristic,
            "heartbeat goes to the weight characteristic",
        )
        assertNull(TimemoreProtocol.tare(r).single().characteristic)
        val start = TimemoreProtocol.onConnect(r).single()
        assertContentEquals(b(0x02, 0x00), start.bytes)
        assertEquals(uuid16("2a9d"), start.characteristic, "the stream request goes to the weight characteristic")
    }

    // Timemore Dot ----------------------------------------------------------------------------

    private fun dotWeight(weight10: Int): ByteArray {
        // A5 5A 01 01, payload length 9 (BE), weight×10 as signed 32-bit BE, 5 more payload bytes, CRC (unchecked).
        val w = b((weight10 shr 24) and 0xFF, (weight10 shr 16) and 0xFF, (weight10 shr 8) and 0xFF, weight10 and 0xFF)
        return hex("a5 5a 01 01 00 09") + w + b(0, 0, 0, 0, 0, 0x12, 0x34)
    }

    @Test
    fun timemoreDotWeightAcrossJunkAndSplits() {
        val d = TimemoreDotProtocol.newDecoder("TIMEMORE_Dot")
        val f = dotWeight(365)
        assertTrue(d.feed(b(0x00, 0xA5, 0x13) + f.copyOfRange(0, 7)).isEmpty())
        assertEquals(listOf(36.5f), d.feed(f.copyOfRange(7, f.size)).map { it.weightG })
        val other = hex("a5 5a 02 13 00 01 07 00 00")
        assertEquals(listOf(-1.2f, 216.8f), d.feed(dotWeight(-12) + other + dotWeight(2168)).map { it.weightG })
        // A false header with an absurd length does not stall the stream.
        assertEquals(listOf(10f), d.feed(hex("a5 5a 01 01 ff ff") + dotWeight(100)).map { it.weightG })
    }

    @Test
    fun timemoreDotCommands() {
        val r = TimemoreDotProtocol.routes("")[0]
        assertEquals(
            listOf("13", "08", "05", "02", "06", "0c").map { hex("a5 5a 02 $it 00 00 00 00").toList() },
            TimemoreDotProtocol.onConnect(r).map { it.bytes.toList() },
        )
        assertContentEquals(hex("a5 5a 03 0d 00 00 d1 7f"), TimemoreDotProtocol.tare(r).single().bytes)
    }

    // DiFluid ---------------------------------------------------------------------------------

    private fun difluidWeight(weight10: Int) =
        DifluidProtocol.frame(
            0x03,
            0x00,
            (weight10 shr 24) and 0xFF,
            (weight10 shr 16) and 0xFF,
            (weight10 shr 8) and 0xFF,
            weight10 and 0xFF,
            *IntArray(9),
        )

    @Test
    fun difluidWeightJunkAndChecksum() {
        val d = DifluidProtocol.newDecoder("Microbalance")
        val f = difluidWeight(365)
        assertEquals(19, f.size)
        assertTrue(d.feed(b(0x11, 0xDF, 0x00) + f.copyOfRange(0, 8)).isEmpty())
        assertEquals(listOf(36.5f), d.feed(f.copyOfRange(8, f.size)).map { it.weightG })
        assertEquals(listOf(-1.2f), d.feed(difluidWeight(-12)).map { it.weightG })
        assertTrue(d.feed(difluidWeight(365).also { it[it.size - 1] = 0 }).isEmpty())
        assertEquals(listOf(10f), d.feed(difluidWeight(100)).map { it.weightG }, "recovers after a bad frame")
    }

    @Test
    fun difluidCommands() {
        val r = DifluidProtocol.routes("")[0]
        assertEquals(uuid16("00ee"), r.service)
        val (grams, auto) = DifluidProtocol.onConnect(r).map { it.bytes }
        assertContentEquals(hex("df df 01 04 01 00 c4"), grams)
        assertContentEquals(hex("df df 01 00 01 01 c1"), auto)
        // Upstream's own literal for the heartbeat: DF DF 03 05 00 C6.
        assertContentEquals(hex("df df 03 05 00 c6"), DifluidProtocol.heartbeat(r).single().bytes)
        assertContentEquals(hex("df df 03 02 01 01 c5"), DifluidProtocol.tare(r).single().bytes)
        assertEquals(2_000L, DifluidProtocol.heartbeatMs)
    }

    // Eclair ----------------------------------------------------------------------------------

    @Test
    fun eclairWeightBatteryAndChecksum() {
        val d = EclairProtocol.newDecoder("ECLAIR-1")
        // 36.5 g = 36500 mg = 0x00008E94, little-endian.
        val w = EclairProtocol.packet(EclairProtocol.WEIGHT, 0x94, 0x8E, 0x00, 0x00, 0, 0, 0, 0)
        assertEquals(10, w.size)
        assertEquals(listOf(36.5f), d.feed(w).map { it.weightG })
        val neg = EclairProtocol.packet(EclairProtocol.WEIGHT, 0x18, 0xFC, 0xFF, 0xFF, 0, 0, 0, 0) // -1000 mg
        assertEquals(listOf(-1f), d.feed(neg).map { it.weightG })
        assertTrue(d.feed(w.copyOf().also { it[9] = (it[9] + 1).toByte() }).isEmpty())
        assertTrue(d.feed(b(0x00) + w).isEmpty(), "one packet per notification, header first")
        assertEquals(64, d.feed(EclairProtocol.packet(EclairProtocol.BATTERY, 64)).single().batteryPercent)
        val r = EclairProtocol.routes("")[0]
        assertContentEquals(hex("54 01 01"), EclairProtocol.tare(r).single().bytes)
        assertContentEquals(hex("43 00 00"), EclairProtocol.heartbeat(r).single().bytes)
    }

    // iKape -----------------------------------------------------------------------------------

    private fun ikapeWeight(hi: Int, lo: Int): ByteArray {
        val f = hex("a7 00 01 0e 13 01 01 00 00") + b(hi, lo, 0x01, 0, 0, 0, 0, 0, 0, 0, 0x7A)
        var s = 0
        for (i in 2..17) s += f[i].toInt() and 0xFF
        f[18] = s.toByte()
        return f
    }

    @Test
    fun ikapeWeightJunkAndChecksum() {
        val d = IkapeProtocol.newDecoder("IK-Cafe")
        val f = ikapeWeight(0x04, 0xB3) // upstream anchor: 04 B3 = 120.3 g
        assertEquals(20, f.size)
        val ack = hex("a7 00 01 02 02 01 06 7a")
        assertTrue(d.feed(b(0x00, 0x7A) + ack + f.copyOfRange(0, 10)).isEmpty())
        assertEquals(listOf(120.3f), d.feed(f.copyOfRange(10, 20)).map { it.weightG })
        assertTrue(d.feed(ikapeWeight(0x04, 0xB3).also { it[18] = 0 }).isEmpty())
        assertEquals(listOf(0f), d.feed(ikapeWeight(0, 0)).map { it.weightG })
    }

    @Test
    fun ikapeCommands() {
        val r = IkapeProtocol.routes("")[0]
        assertContentEquals(hex("a7 00 01 02 04 05 0c 7a"), IkapeProtocol.onConnect(r).single().bytes)
        val tare = IkapeProtocol.tare(r).single()
        assertContentEquals(hex("a7 00 01 02 02 01 06 7a"), tare.bytes)
        assertTrue(tare.withResponse)
    }

    // Insmart, MyScale, Precisa, Tencent ------------------------------------------------------

    @Test
    fun insmartWeight() {
        val d = InsmartProtocol.newDecoder("863A")
        assertEquals(listOf(36.5f), d.feed(hex("12 06 05 00 6d 01 08 00")).map { it.weightG })
        assertTrue(d.feed(hex("00 12 06 05 00 6d 01 08")).isEmpty())
        val r = InsmartProtocol.routes("")[0]
        assertEquals(listOf(uuid16("fff3")), r.notify)
        assertContentEquals(b(0x50, 0x00), InsmartProtocol.tare(r).single().bytes)
    }

    @Test
    fun myScaleWeight() {
        val d = MyScaleProtocol.newDecoder("my_scale")
        // 36.5 g = 36500 mg = 0x8E94 in [3..6]; [2] high nibble 8 or C = negative.
        val f = hex("00 00 00 00 00 8e 94 00 00 00 00 00 00 00 00")
        assertEquals(listOf(36.5f), d.feed(f).map { it.weightG })
        assertEquals(listOf(-36.5f), d.feed(f.copyOf().also { it[2] = 0xC0.toByte() }).map { it.weightG })
        assertTrue(d.feed(f.copyOf(14)).isEmpty())
        val tare = MyScaleProtocol.tare(MyScaleProtocol.routes("")[0]).single().bytes
        assertEquals(20, tare.size)
        assertContentEquals(hex("ac 40"), tare.copyOf(2))
        assertContentEquals(hex("d2 d2"), tare.copyOfRange(18, 20))
    }

    @Test
    fun precisaWeight() {
        val d = PrecisaProtocol.newDecoder("CFS-9002")
        val f = hex("aa 09 41 00 00 00 00 6d 01 00 00")
        assertEquals(listOf(36.5f), d.feed(f).map { it.weightG })
        assertEquals(listOf(-36.5f), d.feed(f.copyOf().also { it[6] = 1 }).map { it.weightG })
        assertTrue(d.feed(f + b(0)).isEmpty())
        assertTrue(d.feed(hex("aa 09 42 00 00 00 00 6d 01 00 00")).isEmpty())
        assertContentEquals(hex("aa 02 31 31"), PrecisaProtocol.tare(PrecisaProtocol.routes("")[0]).single().bytes)
    }

    @Test
    fun tencentWeight() {
        val d = TencentProtocol.newDecoder("DSD TECH")
        val f = hex("a7 00 24 0c 13 00 00 00 00 01 6d 00 00 00 00 00 00 00")
        assertEquals(listOf(36.5f), d.feed(f).map { it.weightG })
        assertEquals(listOf(-36.5f), d.feed(f.copyOf().also { it[7] = 0x10 }).map { it.weightG })
        assertTrue(d.feed(f.copyOf(17)).isEmpty())
        assertTrue(d.feed(f.copyOf().also { it[2] = 0x25 }).isEmpty())
    }

    // Varia -----------------------------------------------------------------------------------

    @Test
    fun variaFramesFromTheLibraryComments() {
        val d = VariaProtocol.newDecoder("Varia AKU")
        // [FA 01] 03 10 02 CD {DD}: sign bit set, 0x02CD = 717 → -7.17 g. [FA 85] 01 4B {CF}: 75 %.
        val r = d.feed(hex("fa 01 03 10 02 cd dd fa 85 01 4b cf"))
        assertEquals(listOf(-7.17f, null), r.map { it.weightG })
        assertEquals(75, r[1].batteryPercent)
        assertContentEquals(hex("fa 01 03 00 0e 42 4e"), VariaProtocol.frame(0x01, 0x00, 0x0E, 0x42))
    }

    @Test
    fun variaResyncsAndRejectsBadChecksums() {
        val d = VariaProtocol.newDecoder("Varia AKU")
        val w = VariaProtocol.frame(0x01, 0x00, 0x0E, 0x42) // 0x0E42 = 3650 → 36.5 g
        val timer = hex("fa 87 02 00 02 87")
        assertTrue(d.feed(b(0x00, 0x13) + timer + w.copyOfRange(0, 3)).isEmpty())
        assertEquals(listOf(36.5f), d.feed(w.copyOfRange(3, w.size)).map { it.weightG })
        assertTrue(d.feed(w.copyOf().also { it[6] = 0 }).isEmpty())
        assertEquals(listOf(36.5f), d.feed(w).map { it.weightG })
        assertContentEquals(hex("fa 82 01 01 82"), VariaProtocol.tare(VariaProtocol.routes("")[0]).single().bytes)
    }

    // WeighMyBru ------------------------------------------------------------------------------

    @Test
    fun weighMyBruReadsBookooFramesOnNordicUart() {
        val d = WeighMyBruProtocol.newDecoder("WeighMyBru")
        val f = bookooFrame(3650, 0, 0)
        assertTrue(d.feed(b(0x00, 0x03, 0x0A) + f.copyOfRange(0, 9)).isEmpty())
        assertEquals(listOf(36.5f), d.feed(f.copyOfRange(9, 20)).map { it.weightG })
        assertEquals(listOf(-0.3f), d.feed(bookooFrame(30, 0, 0, negative = true)).map { it.weightG })
        assertTrue(d.feed(bookooFrame(3650, 0, 0).also { it[8] = 9 }).isEmpty())
    }

    @Test
    fun weighMyBruCommands() {
        val r = WeighMyBruProtocol.routes("")[0]
        assertEquals("6e400003-b5a3-f393-e0a9-e50e24dcca9e", r.write)
        assertContentEquals(hex("03 0a 01 01 00 09"), WeighMyBruProtocol.tare(r).single().bytes)
        assertContentEquals(hex("07 00 00 00 00 00 07"), WeighMyBruProtocol.onConnect(r).single().bytes)
        assertEquals(
            listOf("02 02", "07 00 00 00 00 00 07", "00").map { hex(it).toList() },
            WeighMyBruProtocol.heartbeat(r).map { it.bytes.toList() },
        )
    }

    // Wilfa -----------------------------------------------------------------------------------

    @Test
    fun wilfaWeightAndGenerations() {
        val d = WilfaProtocol.newDecoder("Wilfa Svart scale")
        assertEquals(listOf(36.5f), d.feed(hex("fa 01 01 6d")).map { it.weightG })
        assertEquals(listOf(-1.2f), d.feed(hex("fa 01 ff f4")).map { it.weightG })
        assertTrue(d.feed(hex("fa 04 01")).isEmpty())
        assertTrue(d.feed(hex("fa 06 00 00")).isEmpty())
        val (gen1, gen2) = WilfaProtocol.routes("")
        assertEquals(uuid16("fee7"), gen1.writeService, "Gen 1 takes commands in its own service")
        assertEquals(uuid16("fec7"), gen1.write)
        assertEquals(listOf(uuid16("ffb1"), uuid16("ffb2")), gen1.notify)
        assertNull(gen2.writeService)
        assertEquals(uuid16("ffb1"), gen2.write)
        assertContentEquals(hex("f5 10"), WilfaProtocol.tare(gen2).single().bytes)
    }
}
