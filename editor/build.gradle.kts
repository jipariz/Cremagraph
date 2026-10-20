// The editor on its own, for the desktop and the web: open a video (or reopen one), edit it, style its graph, export.
plugins {
    id("onlyshots.feature")
    id("onlyshots.kmp.wasm")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.open)
            api(projects.feature.edit)
            api(projects.feature.style)
            implementation(projects.storage)
            implementation(libs.sqldelight.runtime)
        }
    }
}
