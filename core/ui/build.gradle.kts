// Shared UI plumbing for the feature modules: the window layout decision and wide panes, the platform composables
// (video surface, pickers, permissions), the routes.
plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
    id("onlyshots.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.designsystem)
            api(projects.core.model)
            api(projects.video)
            // A shot fetched from visualizer.coffee, offered wherever a shot is picked.
            api(projects.visualizer)
            api(libs.okio)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.haze)
            api(libs.compose.adaptive)
            api(libs.compose.adaptive.layout)
            api(libs.compose.adaptive.navigation)
            // The routes are Navigation 3 keys, and every host shows them with NavDisplay.
            api(libs.navigation3.ui)
            api(libs.lifecycle.viewmodel.navigation3)
        }
        // The iOS video surface reuses the camera's pixel-buffer conversion.
        iosMain.dependencies { implementation(projects.camera) }
        androidMain.dependencies {
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.common)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
        }
    }
}
