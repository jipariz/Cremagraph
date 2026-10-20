plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("onlyshots.compose")
}

kotlin {
    // Browsers without WasmGC get the Kotlin/JS build; composeCompatibilityBrowserDistribution ships both and loads the
    // one the browser can run.
    js {
        browser { commonWebpackConfig { outputFileName = "onlyshots.js" } }
        binaries.executable()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "onlyshots.js" } }
        binaries.executable()
    }

    sourceSets {
        webMain.dependencies {
            implementation(projects.editor)
            implementation(projects.storage)
            implementation(projects.core.resources)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.browser)
            // The browser's back button and history, tied to the editor's Navigation 3 back stack.
            implementation(libs.navigation3.browser)
        }
    }
}
