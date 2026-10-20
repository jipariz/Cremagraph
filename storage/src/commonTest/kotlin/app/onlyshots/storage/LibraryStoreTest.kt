package app.onlyshots.storage

import app.onlyshots.core.model.Sidecar
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class LibraryStoreTest {
    private val fs = FakeFileSystem()
    private val store = LibraryStore(fs, "/library".toPath())

    @AfterTest fun tearDown() = fs.checkNoOpenFiles()

    @Test
    fun writesAndReadsSidecarNextToVideo() {
        val item = videoItem("a", 100)
        store.write(item)
        assertTrue(fs.exists("/library/videos/a.onlyshots.json".toPath()))
        assertEquals(item, store.read("a"))
        assertNull(store.read("missing"))
    }

    @Test
    fun renamesSidecarsFromBeforeTheRename() {
        val item = videoItem("a", 100)
        store.write(item)
        fs.atomicMove(store.sidecarPath("a"), store.videosDir / "a${Sidecar.LEGACY_SUFFIX}")
        val reopened = LibraryStore(fs, "/library".toPath())
        assertEquals(item, reopened.read("a"))
        assertFalse(fs.exists(store.videosDir / "a${Sidecar.LEGACY_SUFFIX}"))
    }

    @Test
    fun scanSkipsUnreadableSidecarsWithoutDeletingThem() {
        store.write(videoItem("a", 100))
        val bad = store.videosDir / Sidecar.fileName("b")
        fs.write(bad) { writeUtf8("{\"schema\": 9, \"video\": {}}") }
        val scan = store.scan()
        assertEquals(listOf("a"), scan.videos.map { it.item.id })
        assertEquals(1, scan.unreadable.size)
        assertTrue(fs.exists(bad))
    }

    @Test
    fun deleteRemovesEverything() {
        val item = videoItem("a", 100)
        fs.write(store.videoPath(item)) { writeUtf8("mp4") }
        fs.write(store.thumbnailPath("a")) { writeUtf8("jpg") }
        store.write(item)
        store.delete(item)
        assertFalse(fs.exists(store.videoPath(item)))
        assertFalse(fs.exists(store.sidecarPath("a")))
        assertFalse(fs.exists(store.thumbnailPath("a")))
    }

    @Test
    fun importCopiesIntoLibrary() {
        val src = "/photos/IMG_0001.MOV".toPath()
        fs.createDirectories(src.parent!!)
        fs.write(src) { writeUtf8("movie") }
        val (id, file) = store.importCopy(src, extension = "mov")
        assertEquals("$id.mov", file)
        assertEquals("movie", fs.read(store.videosDir / file) { readUtf8() })
        assertTrue(fs.exists(src), "original stays where it was")
    }

    @Test
    fun settingsRoundTripAndFallBack() {
        val path = "/library/settings.json".toPath()
        val settings = SettingsStore(fs, path)
        assertEquals(AppSettings(), settings.load())
        val changed = AppSettings(firstRunDone = true, host = "192.168.1.40")
        settings.save(changed)
        assertEquals(changed, settings.load())
        fs.write(path) { writeUtf8("not json") }
        assertEquals(AppSettings(), settings.load())
    }

    @Test
    fun shareTempFilesExpireAfter24Hours() {
        val temp = ShareTempFiles(fs, "/cache/share".toPath())
        val file = temp.newFile("a", 0)
        fs.write(file) { writeUtf8("x") }
        val made = fs.metadata(file).lastModifiedAtMillis!!
        assertEquals(0, temp.cleanup(made + ShareTempFiles.MAX_AGE_MS - 1))
        assertEquals(1, temp.cleanup(made + ShareTempFiles.MAX_AGE_MS))
        assertFalse(fs.exists(file))
    }
}
