// The editor: timeline, graph placement, its sheets and panel, export.
plugins {
    id("onlyshots.feature")
    id("onlyshots.kmp.wasm")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.style)
            implementation(projects.storage)
            implementation(libs.compose.ui.backhandler)
        }
    }
}
