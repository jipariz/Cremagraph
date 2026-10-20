package app.onlyshots.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryIndexTest {
    private fun index() = SqlLibraryIndex(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { SqlLibraryIndex.Schema.create(it) },
    )

    @Test fun listsNewestFirstWithUnlinkedBadge() {
        val index = index()
        index.upsert(videoItem("old", 100), 1)
        index.upsert(videoItem("new", 300), 1)
        index.upsert(videoItem("import", 200, linked = false), 1)
        val all = index.all()
        assertEquals(listOf("new", "import", "old"), all.map { it.id })
        assertEquals(listOf(true, false, true), all.map { it.linked })
        assertEquals("Zer0", all.first().profileName)
    }

    @Test fun reconcileMakesSidecarsWin() {
        val fs = FakeFileSystem()
        val store = LibraryStore(fs, "/lib".toPath())
        val index = index()
        // Stale row without a sidecar, and a sidecar the index has never seen.
        index.upsert(videoItem("ghost", 50), 1)
        store.write(videoItem("a", 100))
        index.reconcile(store.scan())
        assertEquals(listOf("a"), index.all().map { it.id })

        // The sidecar changed behind the index's back (e.g. an older app build wrote it).
        store.write(videoItem("a", 100, linked = false))
        index.reconcile(store.scan())
        assertEquals(false, index.all().single().linked)
    }

    @Test fun handlesFiveHundredVideos() {
        val index = index()
        repeat(500) { index.upsert(videoItem("v$it", it.toLong()), 1) }
        val all = index.all()
        assertEquals(500, all.size)
        assertEquals("v499", all.first().id)
    }
}
