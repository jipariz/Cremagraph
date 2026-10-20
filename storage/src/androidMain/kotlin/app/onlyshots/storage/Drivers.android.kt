package app.onlyshots.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

fun createLibraryDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(SqlLibraryIndex.Schema, context, "library.db")
