package app.onlyshots

import kotlin.time.Instant

/** "m:ss": a video's or a shot's length, from microseconds. */
fun clockLength(us: Long): String {
    val seconds = us / 1_000_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** "2026-10-19 08:11": when something was filmed or pulled, to the minute, as the instant reads in UTC. */
fun Instant.minuteStamp(): String = toString().take(16).replace('T', ' ')
