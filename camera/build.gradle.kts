plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.kotlinx.coroutines.core)
            api(libs.compose.runtime)
            api(libs.compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.video)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        getByName("desktopMain").dependencies { implementation(libs.compose.foundation) }
        iosMain.dependencies { implementation(libs.compose.foundation) }
    }
}
