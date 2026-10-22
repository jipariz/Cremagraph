// visualizer.coffee: fetching a shot someone shared there, by its share code or its link. Every platform, the web too
// (the visualizer allows any origin).
plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        getByName("desktopMain").dependencies { implementation(libs.ktor.client.cio) }
        webMain.dependencies { implementation(libs.ktor.client.js) }
        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
        getByName("androidHostTest").dependencies {
            // Fake visualizer for the client test.
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.client.cio)
        }
    }
}
