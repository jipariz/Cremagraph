package app.onlyshots.editor

import app.cash.sqldelight.db.SqlDriver
import app.onlyshots.edit.EditViewModel
import app.onlyshots.export.ExportManager
import app.onlyshots.open.OpenViewModel
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.Library
import app.onlyshots.storage.LibraryIndex
import app.onlyshots.storage.LibraryStore
import app.onlyshots.storage.SettingsStore
import app.onlyshots.storage.ShareTempFiles
import app.onlyshots.storage.SqlLibraryIndex
import app.onlyshots.storage.VideoImport
import app.onlyshots.style.StyleEditorViewModel
import app.onlyshots.style.StyleLibrary
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier of the platform cache directory [Path]. */
const val CACHE_DIR = "cacheDir"

/** Qualifier of the library directory [Path]: videos, sidecars, thumbnails and settings. */
const val LIBRARY_DIR = "libraryDir"

/**
 * What the editor needs, on every platform: the library and its index, the settings, the styles, the export manager,
 * and the open, edit and style-editor view models. A host adds its platform module (file system, directories, exporter,
 * probe, share, gallery) and a `ShotHistory` (a machine's shots, or `ShotHistory.None`).
 */
val editorModule = module {
    single { LibraryStore(get(), get<Path>(named(LIBRARY_DIR))) }
    // The SQL index where the platform has a database; the web's session-only library goes without.
    single<LibraryIndex> { getOrNull<SqlDriver>()?.let(::SqlLibraryIndex) ?: LibraryIndex.None }
    single<VideoImport> { VideoImport.Copy(get(), get()) }
    single { Library(get(), get(), get()) }
    single { SettingsStore(get(), get<Path>(named(LIBRARY_DIR)) / "settings.json") }
    single { ShareTempFiles(get(), get<Path>(named(CACHE_DIR)) / "share") }
    single { AppSettingsRepo(get()) }
    single { StyleLibrary(get()) }
    single {
        ExportManager(
            store = get(),
            exporter = get(),
            temp = get(),
            share = get(),
            gallery = get(),
            background = get(),
            nowMs = { Clock.System.now().toEpochMilliseconds() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    viewModel { (id: String) ->
        EditViewModel(id, get(), get(), get(), get(), get(), get(), get<Path>(named(CACHE_DIR)) / "edit", get(), get())
    }
    viewModel { OpenViewModel(get(), get(), get(), get(), get()) }
    // Koin parameters cannot be null; an absent id travels as "".
    viewModel { (baseId: String, editId: String, videoId: String) ->
        StyleEditorViewModel(
            baseId.ifEmpty { null },
            editId.ifEmpty { null },
            videoId.ifEmpty { null },
            get(),
            get(),
            get(),
            get(),
            get<Path>(named(CACHE_DIR)) / "style",
        )
    }
}
