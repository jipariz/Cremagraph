// The desktop and web editor's start: open a video, or reopen one.
plugins {
    id("onlyshots.feature")
    id("onlyshots.kmp.wasm")
}

kotlin { sourceSets { commonMain.dependencies { implementation(projects.storage) } } }
