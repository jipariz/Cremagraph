package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

/** The JSON a GaggiMate's Shot Analyzer exports, and that [ShotFiles] tells it apart from everything else. */
class GaggiMateShotFileTest {
    private fun file(id: String = "123", timestamp: Long? = 1_792_338_179, extra: String = "") =
        """{"id":"$id","profile":"Londinium","profileId":"abc",""" +
            (timestamp?.let { """"timestamp":$it,""" } ?: "") +
            """"duration":750,"volume":36.2,"rating":4,"incomplete":false,$extra"samples":[""" +
            """{"t":0,"tt":93,"ct":93.1,"tp":9,"cp":1.2,"fl":2.1,"tf":2,"pf":1.8,"vf":0,"v":0,"ev":0,""" +
            """"pr":0,"wp":0,"systemInfo":{},"phaseNumber":0,"phaseDisplayNumber":1},""" +
            """{"t":250,"tt":93,"ct":93,"tp":9,"cp":6.2,"fl":2.05,"tf":2,"pf":1.75,"vf":1.7,"v":4.2,""" +
            """"ev":4,"pr":0.4,"wp":12.1,"systemInfo":{},"phaseNumber":0,"phaseDisplayNumber":1},""" +
            """{"t":500,"tt":93,"ct":92.9,"tp":9,"cp":8.8,"fl":1.98,"tf":2,"pf":1.7,"vf":1.75,"v":18,""" +
            """"ev":17.8,"pr":0.5,"wp":22.4,"systemInfo":{},"phaseNumber":1,"phaseDisplayNumber":2}]}"""

    @Test
    fun anExportIsReadWhole() {
        val shot = ShotFiles.parse(file(), fallbackId = "fallback")
        assertEquals("123", shot.id)
        assertEquals(SourceKind.GAGGIMATE, shot.source)
        assertEquals("Londinium", shot.profileName)
        assertEquals(Instant.fromEpochSeconds(1_792_338_179), shot.startedAt)
        assertEquals(750_000, shot.durationUs)
        assertEquals(36.2f, shot.finalWeight)

        val second = shot.samples[1]
        assertEquals(250_000, second.tUs)
        assertEquals(6.2f, second.pressure)
        assertEquals(9f, second.targetPressure)
        assertEquals(2.05f, second.flow)
        assertEquals(2f, second.targetFlow)
        assertEquals(93f, second.temperature)
        assertEquals(93f, second.targetTemperature)
        assertEquals(4.2f, second.weight)
        assertEquals(1.7f, second.weightFlow)

        assertEquals(listOf(0, 0, 1), shot.samples.map { it.phase })
        assertEquals(listOf(0L, 500_000L), shot.phaseStartsUs)
    }

    @Test
    fun aFileWithNoClockIsStampedNow() {
        val shot = ShotFiles.parse(file(timestamp = null), "f") { Instant.fromEpochSeconds(42) }
        assertEquals(Instant.fromEpochSeconds(42), shot.startedAt)
    }

    @Test
    fun aFileWithNoIdIsNamedAfterTheFile() {
        val text = file().replace(""""id":"123",""", "")
        assertEquals("shot.json", ShotFiles.parse(text, fallbackId = "shot.json").id)
    }

    @Test
    fun aGaggiuinoFileIsStillAGaggiuinoFile() {
        val text =
            """{"id":42,"timestamp":1760000000,"duration":285,"profileName":"Londinium",""" +
                """"datapoints":{"timeInShot":[0,10],"pressure":[0,60]}}"""
        assertEquals(SourceKind.GAGGIUINO, ShotFiles.parse(text, "f").source)
    }

    @Test
    fun aGaggiMateExportIsNotMistakenForAGaggiuinoOne() {
        assertEquals(SourceKind.GAGGIMATE, ShotFiles.parse(file(), "f").source)
    }

    @Test
    fun aSlogIsReadFromItsBytesByTheSameEntryPoint() {
        val bytes = shotLog(profileName = "Gentle", samples = listOf(sample(t = 0), sample(t = 250)))
        val shot = ShotFiles.parse(bytes, fallbackId = "000123")
        assertEquals(SourceKind.GAGGIMATE, shot.source)
        assertEquals("Gentle", shot.profileName)
    }

    @Test
    fun jsonBytesStillGoThroughTheTextPath() {
        assertEquals(SourceKind.GAGGIMATE, ShotFiles.parse(file().encodeToByteArray(), "f").source)
    }

    @Test
    fun anEmptyExportIsNotAShotFile() {
        val text = """{"id":"1","profile":"x","duration":0,"samples":[]}"""
        assertFailsWith<NotAShotFile> { ShotFiles.parse(text, "f") }
    }

    @Test
    fun aSeriesTheExportLeavesOutIsLeftNull() {
        val text =
            """{"id":"5","profile":"Bare","timestamp":1,"duration":250,"samples":""" +
                """[{"t":0,"ct":93},{"t":250,"ct":93.2}]}"""
        val shot = ShotFiles.parse(text, "f")
        assertEquals(SourceKind.GAGGIMATE, shot.source)
        assertNull(shot.samples.first().pressure)
        assertNull(shot.samples.first().weight)
        assertEquals(93f, shot.samples.first().temperature)
    }
}
