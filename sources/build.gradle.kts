plugins {
    id("onlyshots.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)
            api(libs.kable.core)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        getByName("desktopMain").dependencies { implementation(libs.ktor.client.cio) }
        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
        getByName("androidHostTest").dependencies {
            // Fake machine for the WebSocket transport test.
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.client.cio)
        }
    }
}
