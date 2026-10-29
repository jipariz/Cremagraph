package app.onlyshots.sources

/**
 * The few GaggiMate binaries the history test serves, written out little-endian as `shot_log_format.h` lays them out.
 * The parser's own test in `:core:model` covers the format's corners; this is only enough for a fake machine to hand
 * something real back, and the two cannot share a builder across modules.
 */
internal object GaggiMateBytes {
    fun shotIndex(vararg entries: ByteArray): ByteArray {
        val head = Writer()
        head.ascii("SIDX")
        head.u16(1)
        head.u16(ENTRY_SIZE)
        head.u32(entries.size.toLong())
        head.u32(entries.size + 1L)
        head.pad(INDEX_HEADER_SIZE)
        return entries.fold(head.bytes()) { acc, e -> acc + e }
    }

    fun entry(id: Long, name: String = "Londinium", deleted: Boolean = false): ByteArray {
        val w = Writer()
        w.u32(id)
        w.u32(1_792_338_179)
        w.u32(28_400)
        w.u16(362)
        w.u8(0)
        w.u8(if (deleted) 0x02 else 0x01)
        w.fixed("profile-id", 32)
        w.fixed(name, 48)
        w.pad(ENTRY_SIZE)
        return w.bytes()
    }

    /** A v8 log: 512-byte header, all fourteen fields, [samples] readings a quarter second apart. */
    fun shotLog(profileName: String = "Londinium", samples: Int = 2): ByteArray {
        val w = Writer()
        w.ascii("SHOT")
        w.u8(8)
        w.u8(SAMPLE_SIZE)
        w.u16(HEADER_SIZE)
        w.u16(250)
        w.u16(0)
        w.u32(0x3FFF)
        w.u32(samples.toLong())
        w.u32((samples - 1) * 250L)
        w.u32(1_792_338_179)
        w.fixed("profile-id", 32)
        w.fixed(profileName, 48)
        w.u16(362)
        repeat(12) {
            w.u16(0)
            w.u8(0)
            w.u8(0)
            w.fixed("", 25)
        }
        w.u8(0)
        w.pad(HEADER_SIZE)
        for (i in 0 until samples) {
            w.u32(i * 250L) // t
            listOf(930, 930, 90, 60, 200, 200, 180, 0, 0, 0, 0, 0, 0).forEach(w::u16)
        }
        return w.bytes()
    }

    private const val HEADER_SIZE = 512
    private const val SAMPLE_SIZE = 30
    private const val INDEX_HEADER_SIZE = 32
    private const val ENTRY_SIZE = 128

    private class Writer {
        private val buffer = ArrayList<Byte>()

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

        fun fixed(s: String, width: Int) {
            val text = s.encodeToByteArray().take(width - 1)
            text.forEach { buffer += it }
            repeat(width - text.size) { buffer += 0 }
        }

        fun pad(to: Int) = repeat((to - buffer.size).coerceAtLeast(0)) { buffer += 0 }

        fun bytes(): ByteArray = buffer.toByteArray()
    }
}
