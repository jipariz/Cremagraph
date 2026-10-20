// The overlay engine: styles, the scene builder and the primitives every platform draws. No
// Compose and no Skia, so the web editor can draw the same scene with a 2D canvas; the Compose
// renderer that the phones use lives in :overlay:compose.
plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
}

kotlin { sourceSets { commonMain.dependencies { api(projects.core.model) } } }
