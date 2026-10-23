package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class BeanconquerorShotTest {
    private val now = Instant.fromEpochSeconds(1_000_000)

    private fun brew(flow: String) =
        """
        { "brew": { "config": { "uuid": "694f6b4c", "unix_timestamp": 1792684688 } },
          "preparation": { "name": "Flair pro 3", "type": "FLAIR", "style_type": "ESPRESSO" },
          "brewFlow": { $flow } }
        """
            .trimIndent()

    /** Trimmed from visualizer.coffee/api/shots/6f865be6-a8ec-4639-8949-955943ecb85b, a Flair with a pressure kit. */
    private val flair =
        brew(
            """
            "weight": [
              { "brew_time": "0.0", "timestamp": "09:59:16.635", "actual_weight": 0, "old_weight": 0 },
              { "brew_time": "0.1", "timestamp": "09:59:16.710", "actual_weight": 0, "old_weight": 0 },
              { "brew_time": "20.4", "timestamp": "09:59:37.011", "actual_weight": 28.4, "old_weight": 28.3 },
              { "brew_time": "20.5", "timestamp": "09:59:37.129", "actual_weight": 28.4, "old_weight": 28.4 } ],
            "realtimeFlow": [
              { "brew_time": "0.0", "timestamp": "09:59:16.635", "flow_value": 0, "smoothed_weight": 0 },
              { "brew_time": "0.1", "timestamp": "09:59:16.710", "flow_value": -0.4, "smoothed_weight": 0 },
              { "brew_time": "20.5", "timestamp": "09:59:37.129", "flow_value": 0.111, "smoothed_weight": 28.4 } ],
            "pressureFlow": [
              { "brew_time": "0.1", "timestamp": "09:59:16.707", "actual_pressure": 0.03, "old_pressure": 0.04 },
              { "brew_time": "20.4", "timestamp": "09:59:36.975", "actual_pressure": 0.04, "old_pressure": 0.03 } ],
            "temperatureFlow": [], "waterFlow": [ { "value": 0, "brew_time": "0", "timestamp": "09:59:17.601" } ],
            "customMetrics": {}, "customAxes": []
            """
        )

    @Test
    fun readsABrewTheVisualizerKept() {
        val visualizer = """{ "id": "6f865be6", "profile_title": "Flair pro 3", "brewdata": $flair }"""
        val shot = ShotFiles.parse(visualizer, fallbackId = "f") { now }

        assertEquals("visualizer-6f865be6", shot.id)
        assertEquals(SourceKind.VISUALIZER, shot.source)
        assertEquals("Flair pro 3", shot.profileName)
        assertEquals(Instant.fromEpochSeconds(1_792_684_688), shot.startedAt)
        assertEquals(listOf(0L, 75_000L, 20_376_000L, 20_494_000L), shot.samples.map { it.tUs })
        assertEquals(20_494_000L, shot.durationUs)
        assertEquals(28.4f, shot.finalWeight)
        assertEquals(emptyList(), shot.phaseStartsUs)
    }

    @Test
    fun aBareBeanconquerorExportReadsTheSame() {
        val shot = ShotFiles.parse(flair, fallbackId = "f") { now }

        assertEquals("beanconqueror-694f6b4c", shot.id)
        assertEquals(4, shot.samples.size)
    }

    @Test
    fun sparsePressureLinesUpByTimestampAndLeavesGaps() {
        val shot = ShotFiles.parse(flair, fallbackId = "f") { now }

        // 16.707 is 3 ms from the 16.710 weight reading, 72 ms from 16.635: it lands on the nearer one.
        assertEquals(0.03f, shot.samples[1].pressure)
        assertEquals(0.03f, shot.samples[0].pressure)
        // 36.975 is 36 ms from 37.011; 37.129 is 154 ms away, still within reach.
        assertEquals(0.04f, shot.samples[2].pressure)
        assertEquals(0.04f, shot.samples[3].pressure)
        assertNull(shot.samples[0].temperature, "an empty list is no series")
    }

    @Test
    fun aReadingTooFarFromAnyOtherIsNotBorrowed() {
        val shot =
            ShotFiles.parse(
                brew(
                    """
                    "weight": [ { "timestamp": "10:00:00.000", "actual_weight": 0 },
                                { "timestamp": "10:00:05.000", "actual_weight": 10 } ],
                    "pressureFlow": [ { "timestamp": "10:00:00.100", "actual_pressure": 2 } ]
                    """
                ),
                fallbackId = "f",
            ) {
                now
            }

        assertEquals(2f, shot.samples[0].pressure)
        assertNull(shot.samples[1].pressure)
    }

    @Test
    fun negativeFlowIsClampedLikeTheVisualizerDoes() {
        val shot = ShotFiles.parse(flair, fallbackId = "f") { now }

        assertEquals(0f, shot.samples[1].weightFlow)
    }

    @Test
    fun hiddenShotTimesStillGiveTheCurve() {
        // With the owner's shot times hidden the visualizer rebases every timestamp to midnight and drops the clock.
        val hidden =
            """
            { "brewFlow": {
                "weight": [ { "timestamp": "00:00:00.000", "actual_weight": 0 },
                            { "timestamp": "00:00:01.500", "actual_weight": 3 } ] } }
            """
        val shot = ShotFiles.parse(hidden, fallbackId = "from-file") { now }

        assertEquals("from-file", shot.id)
        assertEquals(now, shot.startedAt)
        assertEquals("Beanconqueror", shot.profileName)
        assertEquals(listOf(0L, 1_500_000L), shot.samples.map { it.tUs })
    }

    @Test
    fun aBrewAcrossMidnightKeepsCountingUp() {
        val late =
            """
            { "brewFlow": {
                "weight": [ { "timestamp": "23:59:59.500", "actual_weight": 0 },
                            { "timestamp": "00:00:00.500", "actual_weight": 2 } ] } }
            """

        assertEquals(listOf(0L, 1_000_000L), ShotFiles.parse(late, fallbackId = "f") { now }.samples.map { it.tUs })
    }

    @Test
    fun withoutWeightTheLongestListIsTheClock() {
        val pressureOnly =
            brew(
                """
                "pressureFlow": [ { "timestamp": "10:00:00.000", "actual_pressure": 1 },
                                  { "timestamp": "10:00:00.100", "actual_pressure": 2 } ]
                """
            )
        val shot = ShotFiles.parse(pressureOnly, fallbackId = "f") { now }

        assertEquals(listOf(1f, 2f), shot.samples.map { it.pressure })
        assertNull(shot.finalWeight)
    }

    @Test
    fun aBrewWithNothingToDrawIsRefused() {
        assertFailsWith<NotAShotFile> {
            ShotFiles.parse(brew(""" "weight": [], "temperatureFlow": [] """), fallbackId = "f") { now }
        }
    }

    @Test
    fun readsTimesOfDay() {
        assertEquals(35_956_635L, BeanconquerorShot.timeOfDayMs("09:59:16.635"))
        assertEquals(1_000L, BeanconquerorShot.timeOfDayMs("00:00:01"))
        assertNull(BeanconquerorShot.timeOfDayMs("9:59"))
    }
}
