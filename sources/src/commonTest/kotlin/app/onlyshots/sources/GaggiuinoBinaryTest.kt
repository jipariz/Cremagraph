package app.onlyshots.sources

import app.onlyshots.sources.machine.Gaggiuino
import app.onlyshots.sources.machine.GaggiuinoDecoder
import app.onlyshots.sources.machine.MachineMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Binary frames from newer Gaggiuino firmware (core 6ea560e), as a real machine sent them while idle. */
internal object GaggiuinoBinaryFrames {
    /** `d_sensor_snap` captured from the machine: idle, 84.4 °C, 0.64 bar, water level 60. */
    val idleSnapshot =
        hex("0a0d645f73656e736f725f736e617012232562c4a8422d62c4a8423599a7243f503c880101900101980101e00101f00101f80101")

    /** A `d_sensor_snap` with `brewActive` and the given readings, encoded the way the firmware does. */
    fun brewing(active: Boolean, pressure: Float, pumpFlow: Float, weight: Float): ByteArray {
        val snap =
            (if (active) byteArrayOf(0x08, 0x01) else ByteArray(0)) +
                float(4, 93f) +
                float(6, pressure) +
                float(7, pumpFlow) +
                float(9, weight)
        return envelope("d_sensor_snap", snap)
    }

    fun envelope(action: String, data: ByteArray): ByteArray {
        val a = action.encodeToByteArray()
        return byteArrayOf(0x0a, a.size.toByte()) + a + byteArrayOf(0x12, data.size.toByte()) + data
    }

    private fun float(field: Int, v: Float): ByteArray {
        val bits = v.toRawBits()
        return byteArrayOf(((field shl 3) or 5).toByte()) + ByteArray(4) { (bits shr (8 * it)).toByte() }
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}

class GaggiuinoBinaryTest {
    private val d = GaggiuinoDecoder()

    @Test
    fun anIdleSnapshotIsAnIdleReading() {
        val sample =
            assertIs<MachineMessage.Reading>(d.decodeBinary(GaggiuinoBinaryFrames.idleSnapshot, 0, "/ws").single())
                .sample
        assertFalse(sample.brewing)
        assertEquals(84.4f, assertNotNull(sample.temperature), 0.05f)
        assertEquals(0.64f, assertNotNull(sample.pressure), 0.01f)
    }

    @Test
    fun theBrewSwitchStartsAndEndsTheShot() {
        val start = d.decodeBinary(GaggiuinoBinaryFrames.brewing(true, 2f, 3f, 0f), 1_000_000, "/ws")
        assertEquals(BrewEvent.Started(1_000_000), assertIs<MachineMessage.Brew>(start.first()).event)
        val mid = d.decodeBinary(GaggiuinoBinaryFrames.brewing(true, 9f, 2f, 18f), 1_300_000, "/ws").single()
        val sample = assertIs<MachineMessage.Reading>(mid).sample
        assertTrue(sample.brewing)
        assertEquals(9f, sample.pressure)
        assertEquals(18f, sample.weight)
        val end = d.decodeBinary(GaggiuinoBinaryFrames.brewing(false, 0f, 0f, 36f), 1_600_000, "/ws")
        // The shot ends at the last snapshot that was still brewing, not when the switch was seen off.
        assertEquals(BrewEvent.Ended(1_300_000), assertIs<MachineMessage.Brew>(end.first()).event)
    }

    @Test
    fun otherTopicsAndJunkAreIgnored() {
        val esp = GaggiuinoBinaryFrames.envelope("d_esp_mem", byteArrayOf(0x08, 0x01))
        assertIs<MachineMessage.Ignored>(d.decodeBinary(esp, 0, "/ws").single())
        assertIs<MachineMessage.Ignored>(d.decodeBinary(byteArrayOf(0x0a, 0x7f, 0x01), 0, "/ws").single())
    }

    @Test
    fun theProbeRecognisesBinaryFirmware() {
        assertTrue(Gaggiuino.isGaggiuinoFrame(GaggiuinoBinaryFrames.idleSnapshot))
        assertTrue(Gaggiuino.isGaggiuinoFrame(GaggiuinoBinaryFrames.envelope("d_sys_state", byteArrayOf(0x08, 0x01))))
        assertFalse(Gaggiuino.isGaggiuinoFrame(GaggiuinoBinaryFrames.envelope("d_esp_mem", ByteArray(0))))
        assertFalse(Gaggiuino.isGaggiuinoFrame(byteArrayOf(1, 2, 3)))
    }
}
