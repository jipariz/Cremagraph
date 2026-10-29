package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The binary shot log and shot index, built here exactly as `shot_log_format.h` lays them out, so a change to either
 * side of the format shows up as a failure rather than as a wrong curve.
 */
class GaggiMateShotLogTest {
    @Test
    fun aV8ShotIsReadWhole() {
        val bytes =
            shotLog(
                startEpoch = 1_792_338_179,
                durationMs = 750,
                profileName = "Londinium",
                finalWeightTenths = 362,
                samples =
                    listOf(
                        sample(t = 0, ct = 931, tt = 930, cp = 12, tp = 90, fl = 210, tf = 200, vf = 0, v = 0),
                        sample(t = 250, ct = 930, tt = 930, cp = 62, tp = 90, fl = 205, tf = 200, vf = 170, v = 42),
                        sample(t = 500, ct = 929, tt = 930, cp = 88, tp = 90, fl = 198, tf = 200, vf = 175, v = 180),
                    ),
                transitions = listOf(0 to 0, 2 to 1),
            )

        val shot = GaggiMateShotLog.parse(bytes, fallbackId = "000123")

        assertEquals("000123", shot.id)
        assertEquals(SourceKind.GAGGIMATE, shot.source)
        assertEquals("Londinium", shot.profileName)
        assertEquals(Instant.fromEpochSeconds(1_792_338_179), shot.startedAt)
        assertEquals(750_000, shot.durationUs)
        assertEquals(36.2f, shot.finalWeight)
        assertEquals(3, shot.samples.size)

        val second = shot.samples[1]
        assertEquals(250_000, second.tUs)
        assertEquals(93.0f, second.temperature)
        assertEquals(93.0f, second.targetTemperature)
        assertEquals(6.2f, second.pressure)
        assertEquals(9.0f, second.targetPressure)
        assertEquals(2.05f, second.flow)
        assertEquals(2.0f, second.targetFlow)
        assertEquals(1.7f, second.weightFlow)
        assertEquals(4.2f, second.weight)
        assertNull(second.targetWeight)

        assertEquals(listOf(0, 0, 1), shot.samples.map { it.phase })
        assertEquals(listOf(0L, 500_000L), shot.phaseStartsUs)
    }

    @Test
    fun aFlowThatDipsBelowZeroStaysNegative() {
        val bytes = shotLog(samples = listOf(sample(t = 0, fl = -25)))
        assertEquals(-0.25f, GaggiMateShotLog.parse(bytes, "1").samples.single().flow)
    }

    @Test
    fun aV6FileHasNoWaterPumpedAndTwentyEightByteSamples() {
        val bytes =
            shotLog(
                version = 6,
                fieldsMask = 0x1FFFL,
                samples = listOf(sample(t = 0, ct = 900, v = 10), sample(t = 250, ct = 905, v = 55)),
            )
        val shot = GaggiMateShotLog.parse(bytes, "7")
        assertEquals(listOf(0L, 250_000L), shot.samples.map { it.tUs })
        assertEquals(90.0f, shot.samples.first().temperature)
        assertEquals(5.5f, shot.finalWeight)
    }

    @Test
    fun aV4FileCountsSamplesByTheRecordingInterval() {
        // Before v6 `t` was a sample index, and before v5 there was no transition table and a 128-byte header.
        val bytes =
            shotLog(
                version = 4,
                headerSize = 128,
                fieldsMask = 0x1FFFL,
                sampleIntervalMs = 100,
                samples = listOf(sample(t = 0, ct = 900), sample(t = 1, ct = 905), sample(t = 2, ct = 910)),
            )
        val shot = GaggiMateShotLog.parse(bytes, "3")
        assertEquals(listOf(0L, 100_000L, 200_000L), shot.samples.map { it.tUs })
        assertEquals(emptyList(), shot.phaseStartsUs)
        assertTrue(shot.samples.all { it.phase == null })
    }

    @Test
    fun aShotStillBeingWrittenIsReadAsFarAsItGoes() {
        // The firmware patches sampleCount and durationMs at the end, so a crashed shot has both at zero.
        val bytes =
            shotLog(sampleCount = 0, durationMs = 0, samples = listOf(sample(t = 0), sample(t = 250), sample(t = 500)))
        val shot = GaggiMateShotLog.parse(bytes, "9")
        assertEquals(3, shot.samples.size)
        assertEquals(500_000, shot.durationUs)
    }

    @Test
    fun aTrailingHalfSampleIsDropped() {
        val bytes = shotLog(samples = listOf(sample(t = 0), sample(t = 250))) + byteArrayOf(1, 2, 3)
        assertEquals(2, GaggiMateShotLog.parse(bytes, "9").samples.size)
    }

    @Test
    fun somethingElseEntirelyIsNotAShotFile() {
        assertTrue(!GaggiMateShotLog.looksLike("{\"id\":1}".encodeToByteArray()))
        assertFailsWith<NotAShotFile> { GaggiMateShotLog.parse("{\"id\":1}".encodeToByteArray(), "1") }
        assertFailsWith<NotAShotFile> { GaggiMateShotLog.parse(shotLog(samples = emptyList()), "1") }
    }

    @Test
    fun theIndexNamesTheShotsAndSkipsTheDeletedOnes() {
        val bytes =
            shotIndex(
                entry(id = 12, timestamp = 1_792_338_179, durationMs = 28_400, volumeTenths = 362, name = "Londinium"),
                entry(
                    id = 11,
                    timestamp = 1_792_330_000,
                    durationMs = 1_000,
                    volumeTenths = 0,
                    name = "Flush",
                    deleted = true,
                ),
                entry(id = 10, timestamp = 1_792_320_000, durationMs = 30_100, volumeTenths = 400, name = "Gentle"),
            )
        val shots = GaggiMateShotLog.index(bytes)
        assertEquals(listOf("12", "10"), shots.map { it.id })
        assertEquals("Londinium", shots.first().profileName)
        assertEquals(Instant.fromEpochSeconds(1_792_338_179), shots.first().startedAt)
        assertEquals(28_400_000, shots.first().durationUs)
        assertEquals(36.2f, shots.first().finalWeight)
        assertEquals(SourceKind.GAGGIMATE, shots.first().source)
    }

    @Test
    fun aShotWithNoWeightHasNone() {
        val shots = GaggiMateShotLog.index(shotIndex(entry(id = 1, volumeTenths = 0, name = "Shot")))
        assertNull(shots.single().finalWeight)
    }

    @Test
    fun anythingThatIsNotAnIndexNamesNothing() {
        assertEquals(emptyList(), GaggiMateShotLog.index("not an index".encodeToByteArray()))
        assertEquals(emptyList(), GaggiMateShotLog.index(ByteArray(0)))
    }

    @Test
    fun theFileNameIsTheIdPaddedToSix() {
        assertEquals("000123.slog", GaggiMateShotLog.fileName("123"))
        assertEquals("1234567.slog", GaggiMateShotLog.fileName("1234567"))
    }
}

// --- the firmware's byte layout, written out ---

internal const val SHOT_LOG_HEADER_SIZE = 512
internal const val SHOT_INDEX_HEADER_SIZE = 32
internal const val SHOT_INDEX_ENTRY_SIZE = 128

/** One sample's scaled integers, in wire order. The caller gives what it cares about; the rest stay zero. */
internal fun sample(
    t: Long,
    tt: Int = 930,
    ct: Int = 930,
    tp: Int = 90,
    cp: Int = 60,
    fl: Int = 200,
    tf: Int = 200,
    pf: Int = 180,
    vf: Int = 0,
    v: Int = 0,
    ev: Int = 0,
    pr: Int = 0,
    si: Int = 0,
    wp: Int = 0,
) = listOf(t.toInt(), tt, ct, tp, cp, fl, tf, pf, vf, v, ev, pr, si, wp)

internal fun shotLog(
    version: Int = 8,
    headerSize: Int = SHOT_LOG_HEADER_SIZE,
    fieldsMask: Long = 0x3FFFL,
    sampleIntervalMs: Int = 250,
    sampleCount: Int? = null,
    durationMs: Long? = null,
    startEpoch: Long = 1_792_338_179,
    profileName: String = "Londinium",
    finalWeightTenths: Int = 0,
    samples: List<List<Int>> = emptyList(),
    transitions: List<Pair<Int, Int>> = emptyList(),
): ByteArray {
    val elapsed = version >= 6
    val fieldCount = (0 until 14).count { fieldsMask and (1L shl it) != 0L }
    val recordSize = if (elapsed) 4 + (fieldCount - 1) * 2 else fieldCount * 2
    val out = Writer(headerSize)
    out.ascii("SHOT")
    out.u8(version)
    out.u8(recordSize)
    out.u16(headerSize)
    out.u16(sampleIntervalMs)
    out.u16(0)
    out.u32(fieldsMask)
    out.u32((sampleCount ?: samples.size).toLong())
    out.u32(durationMs ?: (samples.lastOrNull()?.first()?.toLong()?.times(if (elapsed) 1 else sampleIntervalMs) ?: 0))
    out.u32(startEpoch)
    out.fixed("profile-id", 32)
    out.fixed(profileName, 48)
    out.u16(finalWeightTenths)
    if (headerSize > 128) {
        for (i in 0 until 12) {
            val t = transitions.getOrNull(i)
            out.u16(t?.first ?: 0)
            out.u8(t?.second ?: 0)
            out.u8(0)
            out.fixed(if (t != null) "Phase ${t.second + 1}" else "", 25)
        }
        out.u8(transitions.size)
    }
    out.pad(headerSize)
    for (s in samples) {
        var field = 0
        for (i in 0 until 14) {
            if (fieldsMask and (1L shl i) == 0L) continue
            val value = s[i]
            if (i == 0 && elapsed) out.u32(value.toLong()) else out.u16(value)
            field++
        }
    }
    return out.bytes()
}

internal fun entry(
    id: Long,
    timestamp: Long = 1_792_338_179,
    durationMs: Long = 28_400,
    volumeTenths: Int = 362,
    name: String = "Londinium",
    deleted: Boolean = false,
): ByteArray {
    val out = Writer(SHOT_INDEX_ENTRY_SIZE)
    out.u32(id)
    out.u32(timestamp)
    out.u32(durationMs)
    out.u16(volumeTenths)
    out.u8(0)
    out.u8(if (deleted) 0x02 else 0x01)
    out.fixed("profile-id", 32)
    out.fixed(name, 48)
    out.pad(SHOT_INDEX_ENTRY_SIZE)
    return out.bytes()
}

internal fun shotIndex(vararg entries: ByteArray): ByteArray {
    val out = Writer(SHOT_INDEX_HEADER_SIZE)
    out.ascii("SIDX")
    out.u16(1)
    out.u16(SHOT_INDEX_ENTRY_SIZE)
    out.u32(entries.size.toLong())
    out.u32(entries.size + 1L)
    out.pad(SHOT_INDEX_HEADER_SIZE)
    return entries.fold(out.bytes()) { acc, e -> acc + e }
}

/** Little-endian, which is what the ESP32 writes. */
private class Writer(initial: Int) {
    private val buffer = ArrayList<Byte>(initial)

    fun u8(v: Int) {
        buffer += (v and 0xFF).toByte()
    }

    fun u16(v: Int) {
        u8(v)
        u8(v shr 8)
    }

    fun u32(v: Long) {
        u16(v.toInt())
        u16((v shr 16).toInt())
    }

    fun ascii(s: String) = s.encodeToByteArray().forEach { buffer += it }

    /** A null-terminated name in a fixed slot, as the firmware stores profile ids and names. */
    fun fixed(s: String, width: Int) {
        val text = s.encodeToByteArray().take(width - 1)
        text.forEach { buffer += it }
        repeat(width - text.size) { buffer += 0 }
    }

    fun pad(to: Int) = repeat((to - buffer.size).coerceAtLeast(0)) { buffer += 0 }

    fun bytes(): ByteArray = buffer.toByteArray()
}
