plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.compose")
    alias(libs.plugins.kotlin.serialization)
}

// The phones' app (Start → Home → Record → Edit), the Koin graph every entry point starts (`initKoin`) and each
// platform's module. The entry points are their own modules: :app:androidApp, :app:desktopApp, and the Xcode project,
// which
// links this module's framework.
kotlin {
    androidLibrary {
        // The export notification's icon (androidMain/res).
        androidResources { enable = true }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "OnlyShotsApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.ui)
            api(projects.editor)
            api(projects.feature.home)
            api(projects.feature.open)
            api(libs.koin.core)
            implementation(libs.compose.ui.backhandler)
            implementation(projects.feature.record)
            implementation(projects.feature.edit)
            implementation(projects.feature.style)
            implementation(projects.core.designsystem)
            implementation(projects.core.resources)
            implementation(projects.core.model)
            implementation(projects.overlay.compose)
            implementation(projects.sources)
            implementation(projects.camera)
            implementation(projects.video)
            implementation(projects.storage)
            implementation(libs.compose.components.resources)
            // Window size classes, postures and pane scaffolds: the medium and expanded layouts.
            implementation(libs.compose.adaptive)
            implementation(libs.compose.adaptive.layout)
            implementation(libs.compose.adaptive.navigation)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.core)
            implementation(libs.androidx.media3.common)
            implementation(libs.androidx.window)
            api(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
