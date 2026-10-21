import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.hot.reload)
}

// The desktop editor (`./gradlew :app:desktopApp:run`, or `:app:desktopApp:hotRun --auto` with Compose Hot Reload): the
// editor alone, playing and exporting through FFmpeg (see :video's desktopMain).

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(projects.app.shared)
    implementation(projects.core.resources)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "app.onlyshots.MainKt"
        // The Viewfinder icon from design/logo/kit/desktop, for packaged builds. The running window sets its own
        // (src/main/resources/onlyshots-icon.png, see Main.kt).
        nativeDistributions {
            // onlyshots.version. macOS refuses a 0.x app version, which is why the app started at 1.0.0.
            packageVersion = appVersion.toString()
            // One per OS, each built on its own OS (the release workflow's matrix): `packageDistributionForCurrentOS`.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OnlyShots"
            description = "Your espresso shot, with its graph: the OnlyShots editor"
            vendor = "OnlyShots"
            // The packaged app carries a trimmed Java runtime: these are what suggestRuntimeModules finds (FFmpeg's
            // JavaCPP and coroutines).
            modules("java.instrument", "java.management", "jdk.unsupported")
            macOS { iconFile.set(project.file("icons/onlyshots.icns")) }
            windows { iconFile.set(project.file("icons/onlyshots.ico")) }
            linux { iconFile.set(project.file("icons/onlyshots.png")) }
        }
    }
}
