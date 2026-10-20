package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncTest {
    @Test
    fun mapsSourceTimeOntoVideoTimeline() {
        // Trigger hit 3.02 s into the file (pre-roll), source clock read 120 s at that moment.
        val sync = ClockSync(triggerVideoUs = 3_020_000, triggerSourceUs = 120_000_000)
        assertEquals(3_020_000, sync.toVideoUs(120_000_000))
        assertEquals(13_020_000, sync.toVideoUs(130_000_000))
        assertEquals(120_000_000, sync.toSourceUs(3_020_000))
    }

    @Test
    fun roundTrips() {
        val sync = ClockSync(triggerVideoUs = 2_999_000, triggerSourceUs = 7)
        for (v in listOf(0L, 1L, 2_999_000L, 61_000_123L)) assertEquals(v, sync.toVideoUs(sync.toSourceUs(v)))
    }
}
