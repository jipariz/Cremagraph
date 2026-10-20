// The graph style picker and editor.
plugins {
    id("onlyshots.feature")
    id("onlyshots.kmp.wasm")
}

kotlin { sourceSets { commonMain.dependencies { implementation(projects.storage) } } }
