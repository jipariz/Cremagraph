package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class GaggiuinoShotFileTest {
    private val now = Instant.fromEpochSeconds(1_000_000)

    private fun file(
        id: String = "\"id\": 42,",
        timestamp: String = "\"timestamp\": 1760000000,",
        extra: String = "",
    ) =
        """
        {
          $id
          $timestamp
          "duration": 30,
          "profileName": "Londinium",
          "datapoints": {
            "timeInShot": [0, 10, 20],
            "pressure": [0, 60, 90],
            "pumpFlow": [0, 25, 18],
            "shotWeight": [0, 14, 360],
            "weightFlow": [0, 5, 21],
            "temperature": [930, 931, 928],
            "targetPressure": [90, 90, 90],
            "targetPumpFlow": [20, 20, 20],
            "targetTemperature": [930, 930, 930]
            $extra
          }
        }
        """
            .trimIndent()

    @Test
    fun readsTheDocumentedShape() {
        val shot = GaggiuinoShotFile.parse(file(), fallbackId = "f") { now }

        assertEquals("42", shot.id)
        assertEquals(SourceKind.GAGGIUINO, shot.source)
        assertEquals("Londinium", shot.profileName)
        assertEquals(Instant.fromEpochSeconds(1_760_000_000), shot.startedAt)
        assertEquals(3_000_000, shot.durationUs)
        assertEquals(listOf(0L, 1_000_000L, 2_000_000L), shot.samples.map { it.tUs })
    }

    @Test
    fun everyReadingIsATenthOfWhatIsStored() {
        val shot = GaggiuinoShotFile.parse(file(), fallbackId = "f") { now }
        val last = shot.samples.last()

        assertEquals(9f, last.pressure)
        assertEquals(1.8f, last.flow)
        assertEquals(36f, last.weight)
        assertEquals(2.1f, last.weightFlow)
        assertEquals(92.8f, last.temperature)
        assertEquals(9f, last.targetPressure)
        assertEquals(2f, last.targetFlow)
        assertEquals(93f, last.targetTemperature)
        assertEquals(36f, shot.finalWeight)
    }

    @Test
    fun aSeriesTheFileDoesNotCarryStaysNull() {
        val text = file().replace("\"pressure\": [0, 60, 90],\n", "")

        val shot = GaggiuinoShotFile.parse(text, fallbackId = "f") { now }

        assertTrue(shot.samples.all { it.pressure == null }, "pressure was invented")
        assertEquals(1.8f, shot.samples.last().flow, "the other series still parsed")
    }

    @Test
    fun phaseTransitionsBecomePhases() {
        val transitions = ", \"phaseTransitions\": [{\"t\": 0, \"phase\": 1}, {\"t\": 20, \"phase\": 2}]"

        val shot = GaggiuinoShotFile.parse(file(extra = transitions), fallbackId = "f") { now }

        assertEquals(listOf(0L, 2_000_000L), shot.phaseStartsUs)
        assertEquals(listOf(1, 1, 2), shot.samples.map { it.phase })
    }

    @Test
    fun noPhasesLeavesThemAlone() {
        val shot = GaggiuinoShotFile.parse(file(), fallbackId = "f") { now }

        assertEquals(emptyList(), shot.phaseStartsUs)
        assertNull(shot.samples.first().phase)
    }

    @Test
    fun aWrappedPayloadIsUnwrapped() {
        val shot = GaggiuinoShotFile.parse("""{ "shot": ${file()} }""", fallbackId = "f") { now }

        assertEquals("42", shot.id)
        assertEquals(3, shot.samples.size)
    }

    @Test
    fun aFileWithoutIdOrTimestampFallsBack() {
        val shot = GaggiuinoShotFile.parse(file(id = "", timestamp = ""), fallbackId = "shot-from-file") { now }

        assertEquals("shot-from-file", shot.id)
        assertEquals(now, shot.startedAt)
    }

    @Test
    fun durationFallsBackToTheLastReading() {
        val text = file().replace("\"duration\": 30,", "")

        val shot = GaggiuinoShotFile.parse(text, fallbackId = "f") { now }

        assertEquals(2_000_000, shot.durationUs)
    }

    @Test
    fun somethingElseEntirelyIsRefused() {
        assertFailsWith<NotAShotFile> { GaggiuinoShotFile.parse("not json", "f") { now } }
        assertFailsWith<NotAShotFile> { GaggiuinoShotFile.parse("""{"a":1}""", "f") { now } }
        assertFailsWith<NotAShotFile> { GaggiuinoShotFile.parse("""{"datapoints":{"timeInShot":[]}}""", "f") { now } }
    }

    /** The newer download: one object per reading, real units, milliseconds, the profile's name under `profile`. */
    @Test
    fun readsTheNewerRowShape() {
        val text =
            """
            { "id": 121, "duration": 234, "timestamp": 1792338179,
              "datapoints": [
                { "timeInShot": 100, "pressure": 0.5, "pumpFlow": 0, "weightFlow": 0, "temperature": 84.8,
                  "shotWeight": 0, "targetPressure": 0, "targetPumpFlow": 12, "phaseIndex": 0 },
                { "timeInShot": 300, "pressure": 6.1, "pumpFlow": 5.7, "weightFlow": 1.2, "temperature": 85,
                  "shotWeight": 2.5, "targetPressure": 0, "targetPumpFlow": 12, "phaseIndex": 1 },
                { "timeInShot": 23400, "pressure": 2, "pumpFlow": 1.7, "weightFlow": 1.8, "temperature": 85,
                  "shotWeight": 60.6, "targetPressure": 2, "targetPumpFlow": 0, "phaseIndex": 1 }
              ],
              "profile": { "id": 9, "name": "Flimsy Light 0.3 (Gaggia) (imported)", "phases": [] } }
            """
        val shot = GaggiuinoShotFile.parse(text, fallbackId = "x")
        assertEquals("121", shot.id)
        assertEquals("Flimsy Light 0.3 (Gaggia) (imported)", shot.profileName)
        assertEquals(Instant.fromEpochSeconds(1792338179), shot.startedAt)
        assertEquals(23_400_000L, shot.durationUs)
        assertEquals(listOf(100_000L, 300_000L, 23_400_000L), shot.samples.map { it.tUs })
        assertEquals(6.1f, shot.samples[1].pressure)
        assertEquals(84.8f, shot.samples[0].temperature)
        assertEquals(12f, shot.samples[0].targetFlow)
        assertEquals(60.6f, shot.finalWeight)
        assertEquals(listOf(300_000L), shot.phaseStartsUs)
        assertEquals(1, shot.samples[2].phase)
    }
}
