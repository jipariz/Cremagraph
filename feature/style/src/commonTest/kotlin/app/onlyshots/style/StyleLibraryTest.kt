package app.onlyshots.style

import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_copy_name
import app.onlyshots.core.resources.style_my_style
import app.onlyshots.core.resources.text
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class StyleLibraryTest {
    private val fs = FakeFileSystem()
    private val path = "/library/settings.json".toPath()
    private val library = StyleLibrary(AppSettingsRepo(SettingsStore(fs, path)))

    @Test
    fun aCopyIsANewUserStyle() {
        val copy = library.copyOf(GraphStyles.Aero, "Aero copy")
        assertNotEquals(GraphStyles.Aero.id, copy.id)
        assertFalse(copy.builtIn)
        assertEquals("Aero copy", copy.name)
    }

    @Test
    fun aNewStyleIsNamedAfterItsBaseOrMyStyle() {
        assertEquals(Res.string.style_copy_name.text("Aero"), newStyleName(GraphStyles.Aero))
        assertEquals(Res.string.style_my_style.text(), newStyleName(null))
    }

    @Test
    fun savedStylesAndTheirDefaultsSurviveARestart() {
        val glass =
            library.copyOf(GraphStyles.Gaggiuino, "Morning glass").let {
                it.copy(panel = it.panel.copy(material = Material.GLASS))
            }
        library.save(glass, setOf(SourceKind.GAGGIUINO))
        val reloaded = StyleLibrary(AppSettingsRepo(SettingsStore(fs, path)))
        assertEquals(listOf(glass), reloaded.custom)
        assertEquals(glass, reloaded.resolve(OverlaySettings(), SourceKind.GAGGIUINO))
        assertEquals("instrument", reloaded.resolve(OverlaySettings(), SourceKind.SCALE).id)
    }

    @Test
    fun savingAgainReplacesAndMovesTheDefault() {
        val s = library.copyOf(GraphStyles.Metro, "Ristretto red")
        library.save(s, setOf(SourceKind.GAGGIUINO, SourceKind.SCALE))
        library.save(s.copy(name = "Ristretto"), setOf(SourceKind.SCALE))
        assertEquals(1, library.custom.size)
        assertEquals("Ristretto", library.custom.single().name)
        assertEquals(setOf(SourceKind.SCALE), library.defaultsOf(s.id))
    }

    @Test
    fun renameAndDelete() {
        val s = library.copyOf(GraphStyles.Minimal, "Quiet")
        library.save(s, setOf(SourceKind.DECENT))
        library.rename(s.id, "Very quiet")
        assertEquals("Very quiet", library.custom.single().name)
        library.delete(s.id)
        assertTrue(library.custom.isEmpty())
        assertTrue(library.defaults.isEmpty())
    }

    @Test
    fun pickingAUserStyleCopiesItIntoTheVideoAndABuiltInDoesNot() {
        val s = library.copyOf(GraphStyles.Aero, "Morning glass")
        library.save(s, emptySet())
        val withMine = library.pick(OverlaySettings(), s.id)
        assertEquals(s, withMine.style)
        // Deleted later: the video still draws with its copy.
        library.delete(s.id)
        assertEquals(s, library.resolve(withMine, SourceKind.GAGGIUINO))
        assertNull(library.pick(withMine, "aero").style)
    }
}
