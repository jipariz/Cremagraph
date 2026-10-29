package app.onlyshots

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FormatsTest {
    @Test
    fun lengthsReadAsMinutesAndSeconds() {
        assertEquals("0:00", clockLength(0))
        assertEquals("0:09", clockLength(9_999_999))
        assertEquals("2:05", clockLength(125_000_000))
    }

    @Test
    fun stampsStopAtTheMinute() {
        assertEquals("2026-10-19 08:11", Instant.parse("2026-10-19T08:11:42Z").minuteStamp())
    }
}
