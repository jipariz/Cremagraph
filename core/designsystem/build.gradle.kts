plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
    id("onlyshots.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(projects.core.resources)
            implementation(libs.compose.shimmer)
        }
    }
}
