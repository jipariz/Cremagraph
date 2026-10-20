package app.onlyshots.core.model

import kotlin.math.abs
import kotlin.math.roundToLong

/** Fixed one-decimal formatting that is the same on every platform and locale. */
fun Float.oneDecimal(): String {
    val tenths = (this * 10f).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val a = abs(tenths)
    return "$sign${a / 10}.${a % 10}"
}

/** `m:ss` for recording times and video lengths. */
fun formatClock(us: Long): String {
    val totalSeconds = (us.coerceAtLeast(0L) / 1_000_000L)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
