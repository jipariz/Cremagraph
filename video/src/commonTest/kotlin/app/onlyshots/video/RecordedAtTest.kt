package app.onlyshots.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RecordedAtTest {
    private val at = Instant.parse("2025-03-01T08:15:30Z")

    @Test
    fun readsFfmpegsIsoTime() {
        assertEquals(at, parseRecordedAt("2025-03-01T08:15:30.000000Z"))
    }

    @Test
    fun readsAndroidsCompactTime() {
        assertEquals(at, parseRecordedAt("20250301T081530.000Z"))
    }

    @Test
    fun readsApplesOffsetWithoutAColon() {
        assertEquals(at, parseRecordedAt("2025-03-01T09:15:30+0100"))
    }

    @Test
    fun anUnsetOrUnreadableClockIsUnknown() {
        assertNull(parseRecordedAt(null))
        assertNull(parseRecordedAt(""))
        assertNull(parseRecordedAt("19040101T000000.000Z"))
        assertNull(parseRecordedAt("1970-01-01T00:00:00Z"))
        assertNull(parseRecordedAt("yesterday"))
    }
}
