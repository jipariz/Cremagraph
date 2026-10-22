package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class VisualizerShotTest {
    private val now = Instant.fromEpochSeconds(1_000_000)

    /** Trimmed from visualizer.coffee/api/shots/832f7109-46d5-47f5-86bc-5349de2957d8, a Gaggiuino's: real numbers. */
    private val gaggiuino =
        """
        { "id": "832f7109-46d5-47f5-86bc-5349de2957d8", "duration": 34.9, "profile_title": "Zer0",
          "drink_weight": "48.7", "start_time": "2026-10-22T15:22:50.000Z", "tags": [],
          "timeframe": [0.5, 0.7, 34.9],
          "data": { "espresso_pressure": [0.1, 0.1, 8.5], "espresso_flow": [0, 0, 3.3],
                    "espresso_weight": [0, 0, 48.7], "espresso_flow_weight": [0.4, 0, 2.9],
                    "espresso_pressure_goal": [6, 6, 9], "espresso_flow_goal": [0.3, 0.3, 3.5],
                    "espresso_temperature_mix": [52, 52, 52], "espresso_temperature_goal": [52, 52, 52],
                    "espresso_water_dispensed": [0.0, 0.0, 7.54] } }
        """
            .trimIndent()

    /**
     * Trimmed from visualizer.coffee/api/shots/02196aef-8d8b-4331-8721-433bee462179, a Decent's: strings, -1 goals,
     * phases as sign flips.
     */
    private val decent =
        """
        { "id": "02196aef-8d8b-4331-8721-433bee462179", "duration": 31.248, "profile_title": "Cremina lever machine",
          "drink_weight": "40.1", "start_time": "2026-10-22T16:05:47.000Z",
          "timeframe": ["0.0", "0.057", "0.242", "1.9", "5.02", "31.248"],
          "data": {
            "espresso_pressure": ["0.0", "0.02", "0.02", "0.54", "1.33", "7.43"],
            "espresso_weight": ["0.0", "0.0", "0.0", "0.0", "0.0", "38.13"],
            "espresso_flow_goal": ["-1.0", "-1.0", "-1.0", "-1.0", "-1.0", "-1.0"],
            "espresso_pressure_goal": ["-1.0", "1.0625", "1.0625", 0.625, 1.1875, 7.25],
            "espresso_state_change": ["0.0", "10000000.0", "10000000.0", "-10000000.0", "10000000.0", "10000000.0"],
            "espresso_temperature_basket": ["93.0", "80.84", "81.11", "85.0", "88.0", "88.59"] } }
        """
            .trimIndent()

    @Test
    fun readsAGaggiuinoShotTheVisualizerKept() {
        val shot = ShotFiles.parse(gaggiuino, fallbackId = "f") { now }

        assertEquals("visualizer-832f7109-46d5-47f5-86bc-5349de2957d8", shot.id)
        assertEquals(SourceKind.VISUALIZER, shot.source)
        assertEquals("Zer0", shot.profileName)
        assertEquals(Instant.parse("2026-10-22T15:22:50Z"), shot.startedAt)
        assertEquals(34_900_000, shot.durationUs)
        assertEquals(48.7f, shot.finalWeight)
        assertEquals(listOf(500_000L, 700_000L, 34_900_000L), shot.samples.map { it.tUs })
        val last = shot.samples.last()
        assertEquals(8.5f, last.pressure)
        assertEquals(3.3f, last.flow)
        assertEquals(2.9f, last.weightFlow)
        assertEquals(52f, last.temperature)
        assertEquals(9f, last.targetPressure)
        assertEquals(emptyList(), shot.phaseStartsUs)
    }

    @Test
    fun readsADecentsStringsAndDropsItsMissingGoals() {
        val shot = ShotFiles.parse(decent, fallbackId = "f") { now }

        assertEquals(6, shot.samples.size)
        assertEquals(57_000L, shot.samples[1].tUs)
        assertEquals(0.02f, shot.samples[1].pressure)
        assertNull(shot.samples[0].targetPressure, "-1 is no goal")
        assertEquals(1.0625f, shot.samples[1].targetPressure)
        assertTrue(shot.samples.all { it.targetFlow == null })
        assertEquals(80.84f, shot.samples[1].temperature, "basket when there is no mix")
        assertNull(shot.samples[0].flow, "a series the shot lacks stays empty")
    }

    @Test
    fun aDecentsPhasesStartWhereTheStateChangeFlips() {
        val shot = ShotFiles.parse(decent, fallbackId = "f") { now }

        assertEquals(listOf(0L, 1_900_000L, 5_020_000L), shot.phaseStartsUs)
        assertEquals(listOf(0, 0, 0, 1, 2, 2), shot.samples.map { it.phase })
    }

    @Test
    fun aShotWithHiddenTimesStartsNow() {
        val hidden = gaggiuino.replace("\"start_time\": \"2026-10-22T15:22:50.000Z\",", "")

        assertEquals(now, ShotFiles.parse(hidden, fallbackId = "f") { now }.startedAt)
    }

    @Test
    fun withoutDrinkWeightTheLastWeightIsTheYield() {
        val noYield = gaggiuino.replace("\"drink_weight\": \"48.7\",", "\"drink_weight\": null,")

        assertEquals(48.7f, ShotFiles.parse(noYield, fallbackId = "f") { now }.finalWeight)
    }

    @Test
    fun aShotWithoutTelemetryIsRefused() {
        assertFailsWith<NotAShotFile> {
            ShotFiles.parse("""{"id":"x","timeframe":[],"data":{}}""", fallbackId = "f") { now }
        }
        assertFailsWith<NotAShotFile> {
            ShotFiles.parse("""{"id":"x","timeframe":[1,2],"data":{"espresso_state_change":[0,1]}}""", "f") { now }
        }
    }

    @Test
    fun aGaggiuinosOwnFileStillGoesToItsReader() {
        val own = """{"id": 7, "datapoints": {"timeInShot": [0, 10], "pressure": [0, 90]}}"""

        assertEquals(SourceKind.GAGGIUINO, ShotFiles.parse(own, fallbackId = "f") { now }.source)
    }
}
