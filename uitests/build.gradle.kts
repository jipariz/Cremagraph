// Behaviour tests of whole apps: the phones' OnlyShotsApp and the desktop/web EditorApp, driven through their test
// tags (app.onlyshots.TestTags) with Compose's UI test API, at phone, tablet and foldable sizes. They find nodes by
// tag, never by colour, shape or the casing of a word, so a redesign can't break them by accident.
//
//   ./gradlew :uitests:desktopTest              # JVM, real FFmpeg probe and exporter: the main gate
//
// Its own targets, not onlyshots.kmp.library's: Compose UI tests don't run on the Android host (no device).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("onlyshots.compose")
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.core)
            implementation(projects.editor)
            implementation(projects.core.ui)
            implementation(projects.core.model)
            implementation(projects.storage)
            implementation(projects.video)
            implementation(projects.camera)
            implementation(projects.sources)
        }
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(projects.app.shared)
        }
    }
}

// The flows use the video module's test clip as the camera's recording and the picked video.
tasks
    .withType<Test>()
    .matching { it.name == "desktopTest" }
    .configureEach {
        systemProperty(
            "onlyshots.testClip",
            rootProject.layout.projectDirectory.file("video/testdata/spike_alignment.mp4").asFile.absolutePath,
        )
    }
