// The engine's scene, drawn with Compose: the live overlay on the record and edit screens, and the
// per-frame renderer the exporters hand to Media3 and AVFoundation.
plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
    id("onlyshots.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.overlay)
            implementation(projects.core.resources)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            api(libs.haze)
            api(libs.haze.blur)
            api(libs.haze.glass)
        }
        // Offscreen drawing tests run on the desktop target, where Skia draws into a bitmap with no device.
        getByName("desktopTest").dependencies { implementation(compose.desktop.currentOs) }
    }
}
