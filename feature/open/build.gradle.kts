// The desktop and web editor's start: open a video, or reopen one.
plugins {
    id("onlyshots.feature")
    id("onlyshots.kmp.wasm")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.storage)
            // Back closes the shot sheet where it is a page of a fold rather than a system sheet.
            implementation(libs.compose.ui.backhandler)
        }
    }
}
