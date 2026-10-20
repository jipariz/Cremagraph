package app.onlyshots.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

fun createLibraryDriver(): SqlDriver = NativeSqliteDriver(SqlLibraryIndex.Schema, "library.db")
