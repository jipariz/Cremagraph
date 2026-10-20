package app.onlyshots.video

import kotlin.time.Instant

/**
 * A container's creation time as the platforms hand it over: ISO 8601 from FFmpeg (`2025-03-01T08:15:30.000000Z`) or
 * Android's compact `20250301T081530.000Z`, or Apple's `2025-03-01T09:15:30+0100`. A file that never had its clock set
 * says 1904 or 1970; that, and anything unreadable, is null.
 */
fun parseRecordedAt(text: String?): Instant? {
    val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val iso =
        COMPACT.matchEntire(t)?.destructured?.let { (y, mo, d, h, mi, s, rest) -> "$y-$mo-${d}T$h:$mi:$s$rest" } ?: t
    // Apple writes the offset without a colon (`+0100`), which ISO's extended form wants.
    val withColon = OFFSET.replace(iso) { m -> "${m.groupValues[1]}:${m.groupValues[2]}" }
    val instant = runCatching { Instant.parse(withColon) }.getOrNull() ?: return null
    return instant.takeIf { it >= EARLIEST }
}

private val COMPACT = Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(.*)""")

private val OFFSET = Regex("""([+-]\d{2})(\d{2})$""")

/** Before any phone that films a shot: an unset clock. */
private val EARLIEST = Instant.parse("2000-01-01T00:00:00Z")
