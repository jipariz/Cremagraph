package app.onlyshots.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/** The library index in [file]; the schema is created the first time the file is opened. */
fun createLibraryDriver(file: File): SqlDriver {
    file.parentFile?.mkdirs()
    return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties(), SqlLibraryIndex.Schema)
}
