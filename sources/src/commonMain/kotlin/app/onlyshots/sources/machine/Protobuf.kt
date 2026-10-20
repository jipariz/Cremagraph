package app.onlyshots.sources.machine

/**
 * Just enough of the protobuf wire format to read a message's fields: varints, 32- and 64-bit fixed values, and
 * length-delimited bytes. Unknown fields are kept as they come, so a newer firmware that adds fields still reads.
 */
internal class ProtoField(val number: Int, val wireType: Int, val varint: Long, val bytes: ByteArray?) {
    val bool: Boolean
        get() = varint != 0L

    /** A `float` field: wire type 5, the 32 bits of an IEEE 754 single. */
    val float: Float?
        get() = if (wireType == FIXED32) Float.fromBits(varint.toInt()) else null

    val string: String?
        get() = bytes?.decodeToString()

    companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LENGTH = 2
        const val FIXED32 = 5
    }
}

/** The fields of one message, in order; throws [IllegalArgumentException] on bytes that are not protobuf. */
internal fun protoFields(bytes: ByteArray): List<ProtoField> {
    val out = ArrayList<ProtoField>()
    var i = 0

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(i < bytes.size && shift < 64) { "truncated varint" }
            val b = bytes[i++].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b < 0x80) return result
            shift += 7
        }
    }

    fun fixed(n: Int): Long {
        require(i + n <= bytes.size) { "truncated fixed$n" }
        var v = 0L
        for (k in 0 until n) v = v or ((bytes[i + k].toLong() and 0xff) shl (8 * k))
        i += n
        return v
    }

    while (i < bytes.size) {
        val key = varint()
        val number = (key ushr 3).toInt()
        val type = (key and 7).toInt()
        require(number > 0) { "field number 0" }
        out +=
            when (type) {
                ProtoField.VARINT -> ProtoField(number, type, varint(), null)
                ProtoField.FIXED64 -> ProtoField(number, type, fixed(8), null)
                ProtoField.FIXED32 -> ProtoField(number, type, fixed(4), null)
                ProtoField.LENGTH -> {
                    val n = varint().toInt()
                    require(n >= 0 && i + n <= bytes.size) { "truncated bytes" }
                    ProtoField(number, type, 0, bytes.copyOfRange(i, i + n)).also { i += n }
                }
                else -> throw IllegalArgumentException("wire type $type")
            }
    }
    return out
}
