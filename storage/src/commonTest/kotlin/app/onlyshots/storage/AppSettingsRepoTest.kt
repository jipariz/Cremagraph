package app.onlyshots.storage

import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class AppSettingsRepoTest {
    private val fs = FakeFileSystem()
    private val path = "/library/settings.json".toPath()

    @Test
    fun everyChangeIsVisibleAtOnceAndSurvivesARestart() {
        val repo = AppSettingsRepo(SettingsStore(fs, path))

        repo.update { it.copy(source = SourceKind.GAGGIMATE, host = "192.168.1.9") }
        repo.update { it.copy(recording = it.recording.copy(preRollSeconds = 7)) }

        assertEquals(SourceKind.GAGGIMATE, repo.state.value.source)
        assertEquals("192.168.1.9", repo.state.value.host)
        assertEquals(7, repo.state.value.recording.preRollSeconds)

        // A fresh repo over the same file is what the next app start sees.
        val reloaded = AppSettingsRepo(SettingsStore(fs, path))
        assertEquals(repo.state.value, reloaded.state.value)
    }

    @Test
    fun firstRunIsRememberedOnceItIsDone() {
        val repo = AppSettingsRepo(SettingsStore(fs, path))
        assertTrue(!repo.state.value.firstRunDone)

        repo.update { it.copy(firstRunDone = true) }

        assertTrue(AppSettingsRepo(SettingsStore(fs, path)).state.value.firstRunDone)
    }

    @Test
    fun aStopRuleRoundTripsThroughTheFile() {
        val repo = AppSettingsRepo(SettingsStore(fs, path))

        repo.update { it.copy(recording = it.recording.copy(stopRule = StopRule.Fixed(25))) }

        assertEquals(StopRule.Fixed(25), AppSettingsRepo(SettingsStore(fs, path)).state.value.recording.stopRule)
    }

    @Test
    fun anUnreadableFileFallsBackToDefaults() {
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("{not json") }

        assertEquals(AppSettings(), AppSettingsRepo(SettingsStore(fs, path)).state.value)
    }

    @Test
    fun oldSettingsWithoutStylesStillLoad() {
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("""{"firstRunDone": true, "recording": {"overlay": {"styleId": "gweb"}}}""") }
        val loaded = AppSettingsRepo(SettingsStore(fs, path)).value
        assertTrue(loaded.firstRunDone)
        assertTrue(loaded.customStyles.isEmpty())
        kotlin.test.assertEquals("gweb", loaded.recording.overlay.styleId)
    }
}
