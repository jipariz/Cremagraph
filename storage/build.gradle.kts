plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.okio)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        getByName("desktopMain").dependencies { implementation(libs.sqldelight.sqlite.driver) }
        getByName("androidHostTest").dependencies { implementation(libs.sqldelight.sqlite.driver) }
        commonTest.dependencies {
            implementation(libs.okio.fakefilesystem)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight { databases { create("LibraryDatabase") { packageName.set("app.onlyshots.storage.db") } } }
