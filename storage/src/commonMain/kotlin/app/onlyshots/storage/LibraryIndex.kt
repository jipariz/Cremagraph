package app.onlyshots.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import app.onlyshots.core.model.Origin
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoItem
import app.onlyshots.storage.db.LibraryDatabase
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** One library grid cell. */
data class LibraryEntry(
    val id: String,
    val file: String,
    val createdAt: Instant,
    val durationUs: Long,
    val origin: Origin,
    /** False for imports not yet linked to a shot: shows the badge. */
    val linked: Boolean,
    val profileName: String?,
    val source: SourceKind?,
)

/** A mirror of the sidecars for listing without reading every file; the sidecars stay the truth. */
interface LibraryIndex {
    fun upsert(item: VideoItem, sidecarModifiedMs: Long)

    fun delete(id: String)

    /** Makes the index mirror [scan]; the sidecar wins on any conflict. */
    fun reconcile(scan: ScanResult)

    /** No index: the web editor's session-only library is a handful of videos, read straight from their sidecars. */
    object None : LibraryIndex {
        override fun upsert(item: VideoItem, sidecarModifiedMs: Long) = Unit

        override fun delete(id: String) = Unit

        override fun reconcile(scan: ScanResult) = Unit
    }
}

/** SQLDelight mirror of the sidecars, for listing 500+ videos without reading every file. */
class SqlLibraryIndex(driver: SqlDriver) : LibraryIndex {
    private val q = LibraryDatabase(driver).videoQueries

    fun newestFirst(context: CoroutineContext): Flow<List<LibraryEntry>> =
        q.newestFirst { id, file, createdAtMs, durationUs, origin, linked, profileName, source, _ ->
                LibraryEntry(
                    id = id,
                    file = file,
                    createdAt = Instant.fromEpochMilliseconds(createdAtMs),
                    durationUs = durationUs,
                    origin = Origin.valueOf(origin),
                    linked = linked != 0L,
                    profileName = profileName,
                    source = source?.let { SourceKind.valueOf(it) },
                )
            }
            .asFlow()
            .mapToList(context)

    fun all(): List<LibraryEntry> =
        q.newestFirst { id, file, createdAtMs, durationUs, origin, linked, profileName, source, _ ->
                LibraryEntry(
                    id,
                    file,
                    Instant.fromEpochMilliseconds(createdAtMs),
                    durationUs,
                    Origin.valueOf(origin),
                    linked != 0L,
                    profileName,
                    source?.let { SourceKind.valueOf(it) },
                )
            }
            .executeAsList()

    override fun upsert(item: VideoItem, sidecarModifiedMs: Long) {
        q.upsert(
            id = item.id,
            file_name = item.file,
            created_at_ms = item.createdAt.toEpochMilliseconds(),
            duration_us = item.durationUs,
            origin = item.origin.name,
            linked = if (item.isLinked) 1L else 0L,
            profile_name = item.shot?.profileName,
            source = item.shot?.source?.name,
            sidecar_modified_ms = sidecarModifiedMs,
        )
    }

    override fun delete(id: String) {
        q.deleteById(id)
    }

    /**
     * Makes the index mirror the sidecars on disk: new and changed sidecars are written, rows without a sidecar are
     * removed. The sidecar wins on any conflict.
     */
    override fun reconcile(scan: ScanResult) {
        q.transaction {
            val known = q.modifiedTimes().executeAsList().associate { it.id to it.sidecar_modified_ms }
            val onDisk = scan.videos.associateBy { it.item.id }
            for ((id, stored) in onDisk) {
                if (known[id] != stored.sidecarModifiedMs) upsert(stored.item, stored.sidecarModifiedMs)
            }
            for (id in known.keys - onDisk.keys) q.deleteById(id)
        }
    }

    companion object {
        val Schema = LibraryDatabase.Schema
    }
}
