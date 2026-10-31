package app.onlyshots.core.model

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A GaggiMate shot as the machine stores it: the binary `.slog` served by `GET /api/history/<id>.slog`, and the binary
 * shot index behind `GET /api/history/index.bin` and `/api/history/recent.bin`. Reading them lets the app list the
 * machine's own shots ([app.onlyshots.sources.machine.GaggiMateHistory]) and lets a `.slog` downloaded by hand reach a
 * video on any platform, the web editor included.
 *
 * Layout from `src/display/models/shot_log_format.h` in `jniebuhr/gaggimate` (master
 * `b78beee4dbcc1cd59946256f05e5cafa5450ec60`, 2026-10-18), cross-read against the firmware's own web parser
 * `web/src/pages/ShotHistory/parseBinaryShot.js`. Reference only, nothing copied.
 *
 * Everything is little-endian. A shot file is a header (128 bytes up to v4, 512 from v5) followed by fixed-size
 * samples. Which fields a sample carries is `fieldsMask`, in the bit order below; `t` grew from a `uint16` sample index
 * to a `uint32` millisecond stamp in v6, which is what makes the record 26, 28 or 30 bytes wide. Values are scaled
 * integers: temperatures and pressures by ten, flows and puck resistance by a hundred, weights by ten.
 *
 * A field this app has no use for (puck flow, estimated weight, puck resistance, system-info bits, water pumped) is
 * read past and dropped rather than guessed at.
 */
object GaggiMateShotLog {
    /** `"SHOT"`, the first four bytes of every shot log. */
    private val SHOT_MAGIC = byteArrayOf('S'.code.toByte(), 'H'.code.toByte(), 'O'.code.toByte(), 'T'.code.toByte())

    /** `"SIDX"`, the first four bytes of a shot index. */
    private val INDEX_MAGIC = byteArrayOf('S'.code.toByte(), 'I'.code.toByte(), 'D'.code.toByte(), 'X'.code.toByte())

    /** A deleted shot keeps its index entry; the list skips it. */
    private const val FLAG_DELETED = 0x02

    private const val INDEX_HEADER_SIZE = 32
    private const val SHOT_HEADER_MIN = 128
    private const val TRANSITION_SIZE = 29
    private const val MAX_TRANSITIONS = 12

    fun looksLike(bytes: ByteArray): Boolean = bytes.startsWith(SHOT_MAGIC)

    /**
     * One shot. [fallbackId] names it: the file carries no id of its own, only the name it was saved under.
     *
     * @param now stamps a file whose clock was never set, so the shot still sorts somewhere sensible.
     */
    fun parse(bytes: ByteArray, fallbackId: String, now: () -> Instant = { Clock.System.now() }): Shot {
        if (!looksLike(bytes)) throw NotAShotFile("not a GaggiMate shot log")
        if (bytes.size < SHOT_HEADER_MIN) throw NotAShotFile("shot log header is cut short")
        val version = bytes.u8(4)
        val recordSize = bytes.u8(5)
        val headerSize = bytes.u16(6).takeIf { it >= SHOT_HEADER_MIN } ?: SHOT_HEADER_MIN
        val sampleIntervalMs = bytes.u16(8).takeIf { it > 0 } ?: 250
        val fieldsMask = bytes.u32(12)
        val sampleCount = bytes.u32(16).toInt()
        val durationMs = bytes.u32(20)
        val startEpoch = bytes.u32(24)
        val profileName = bytes.cString(60, 48)
        val finalWeight = bytes.u16(108) / WEIGHT_SCALE
        if (bytes.size < headerSize) throw NotAShotFile("shot log header is cut short")

        val layout = layout(fieldsMask, elapsedTimestamp(version, recordSize, fieldsMask))
        val stride = sampleSize(recordSize, layout)
        if (stride <= 0) throw NotAShotFile("unreadable sample size $recordSize in version $version")
        val whole = (bytes.size - headerSize) / stride
        val count = if (sampleCount in 1..whole) sampleCount else whole
        if (count <= 0) throw NotAShotFile("no readings")

        val transitions = transitions(bytes, version, headerSize)
        val samples = ArrayList<Sample>(count)
        for (i in 0 until count) {
            val f = readSample(bytes, headerSize + i * stride, layout)
            val tMs = if (layout.elapsed) f[FIELD_T] ?: 0f else (f[FIELD_T] ?: 0f) * sampleIntervalMs
            samples +=
                Sample(
                    tUs = (tMs * 1000f).toLong(),
                    pressure = f.scaled(FIELD_CP, PRESSURE_SCALE),
                    flow = f.scaled(FIELD_FL, FLOW_SCALE),
                    weight = f.scaled(FIELD_V, WEIGHT_SCALE),
                    weightFlow = f.scaled(FIELD_VF, FLOW_SCALE),
                    temperature = f.scaled(FIELD_CT, TEMPERATURE_SCALE),
                    targetPressure = f.scaled(FIELD_TP, PRESSURE_SCALE),
                    targetFlow = f.scaled(FIELD_TF, FLOW_SCALE),
                    targetTemperature = f.scaled(FIELD_TT, TEMPERATURE_SCALE),
                    phase = transitions.lastOrNull { it.first <= i }?.second,
                )
        }
        val lastUs = samples.last().tUs
        return Shot(
            id = fallbackId,
            source = SourceKind.GAGGIMATE,
            profileName = profileName.ifBlank { "Shot $fallbackId" },
            startedAt = if (startEpoch > 0) Instant.fromEpochSeconds(startEpoch) else now(),
            durationUs = if (durationMs > 0) durationMs * 1000 else lastUs,
            finalWeight = finalWeight.takeIf { it > 0f } ?: samples.lastOrNull { it.weight != null }?.weight,
            samples = samples,
            phaseStartsUs = transitions.map { samples[it.first.coerceAtMost(samples.lastIndex)].tUs }.distinct(),
        )
    }

    /**
     * The shots an index file names, in the order it holds them (the machine writes `recent.bin` newest first). A
     * deleted shot keeps its entry and is skipped.
     */
    fun index(bytes: ByteArray): List<ShotSummary> {
        if (!bytes.startsWith(INDEX_MAGIC) || bytes.size < INDEX_HEADER_SIZE) return emptyList()
        val entrySize = bytes.u16(6).takeIf { it > 0 } ?: return emptyList()
        val whole = (bytes.size - INDEX_HEADER_SIZE) / entrySize
        val count = bytes.u32(8).toInt().let { if (it in 0..whole) it else whole }
        val out = ArrayList<ShotSummary>(count)
        for (i in 0 until count) {
            val at = INDEX_HEADER_SIZE + i * entrySize
            if (bytes.u8(at + 15) and FLAG_DELETED != 0) continue
            val id = bytes.u32(at)
            val timestamp = bytes.u32(at + 4)
            val name = bytes.cString(at + 48, 48)
            out +=
                ShotSummary(
                    id = id.toString(),
                    source = SourceKind.GAGGIMATE,
                    profileName = name.ifBlank { "Shot $id" },
                    startedAt = Instant.fromEpochSeconds(timestamp),
                    durationUs = bytes.u32(at + 8) * 1000,
                    finalWeight = (bytes.u16(at + 12) / WEIGHT_SCALE).takeIf { it > 0f },
                )
        }
        return out
    }

    /** The file name a shot id is stored under: the id padded to six digits, `000123.slog`. */
    fun fileName(id: String): String = id.padStart(6, '0') + ".slog"

    /**
     * v6 turned `t` from a sample index into a millisecond stamp. One fork's v6 kept the old two-byte index, and the
     * record size written beside the version is what tells the two apart (`shotLogHasElapsedTimestamp` upstream).
     */
    private fun elapsedTimestamp(version: Int, recordSize: Int, fieldsMask: Long): Boolean =
        version >= 6 && !(version == 6 && recordSize == 26 && fieldsMask == 0x1FFFL)

    /** The fields a sample carries, in wire order, each with its width and whether it is signed. */
    private fun layout(fieldsMask: Long, elapsed: Boolean): Layout {
        val present = FIELDS.filter { fieldsMask and it.bit != 0L }
        return Layout(present, elapsed)
    }

    /** The width of one record, or 0 when the file disagrees with itself. */
    private fun sampleSize(recordSize: Int, layout: Layout): Int {
        val expected = layout.fields.sumOf { if (layout.elapsed && it.bit == FIELD_T) 4 else 2 }
        // reserved0 was padding before it held the record size, so an old file says 0 and is taken at the mask's word.
        return if (recordSize == 0 || recordSize == expected) expected else 0
    }

    /**
     * Phase number in effect from each sample index, earliest first; empty before v5, which had no transition table.
     */
    private fun transitions(bytes: ByteArray, version: Int, headerSize: Int): List<Pair<Int, Int>> {
        if (version < 5) return emptyList()
        val countAt = 110 + MAX_TRANSITIONS * TRANSITION_SIZE
        if (headerSize <= countAt) return emptyList()
        val count = bytes.u8(countAt).coerceAtMost(MAX_TRANSITIONS)
        return (0 until count)
            .map { i ->
                val at = 110 + i * TRANSITION_SIZE
                bytes.u16(at) to bytes.u8(at + 2)
            }
            .sortedBy { it.first }
    }

    private fun readSample(bytes: ByteArray, at: Int, layout: Layout): Map<Long, Float> {
        val out = HashMap<Long, Float>(layout.fields.size)
        var offset = at
        for (field in layout.fields) {
            val wide = layout.elapsed && field.bit == FIELD_T
            out[field.bit] =
                if (wide) bytes.u32(offset).toFloat()
                else if (field.signed) bytes.i16(offset).toFloat() else bytes.u16(offset).toFloat()
            offset += if (wide) 4 else 2
        }
        return out
    }

    private fun Map<Long, Float>.scaled(bit: Long, scale: Float): Float? = this[bit]?.div(scale)

    private class Layout(val fields: List<Field>, val elapsed: Boolean)

    private class Field(val bit: Long, val signed: Boolean = false)

    private const val FIELD_T = 0x0001L
    private const val FIELD_TT = 0x0002L
    private const val FIELD_CT = 0x0004L
    private const val FIELD_TP = 0x0008L
    private const val FIELD_CP = 0x0010L
    private const val FIELD_FL = 0x0020L
    private const val FIELD_TF = 0x0040L
    private const val FIELD_PF = 0x0080L
    private const val FIELD_VF = 0x0100L
    private const val FIELD_V = 0x0200L
    private const val FIELD_EV = 0x0400L
    private const val FIELD_PR = 0x0800L
    private const val FIELD_SI = 0x1000L
    private const val FIELD_WP = 0x2000L

    /** Wire order of the sample record; a flow can go slightly negative, so those four are signed. */
    private val FIELDS =
        listOf(
            Field(FIELD_T),
            Field(FIELD_TT),
            Field(FIELD_CT),
            Field(FIELD_TP),
            Field(FIELD_CP),
            Field(FIELD_FL, signed = true),
            Field(FIELD_TF, signed = true),
            Field(FIELD_PF, signed = true),
            Field(FIELD_VF, signed = true),
            Field(FIELD_V),
            Field(FIELD_EV),
            Field(FIELD_PR),
            Field(FIELD_SI),
            Field(FIELD_WP),
        )

    private const val TEMPERATURE_SCALE = 10f
    private const val PRESSURE_SCALE = 10f
    private const val FLOW_SCALE = 100f
    private const val WEIGHT_SCALE = 10f
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

private fun ByteArray.u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)

private fun ByteArray.i16(i: Int): Int = u16(i).toShort().toInt()

private fun ByteArray.u32(i: Int): Long = u16(i).toLong() or (u16(i + 2).toLong() shl 16)

/** A fixed-width, null-terminated name as the firmware writes it. */
private fun ByteArray.cString(i: Int, len: Int): String =
    decodeToString(i, (i + len).coerceAtMost(size)).substringBefore('\u0000').trim()
