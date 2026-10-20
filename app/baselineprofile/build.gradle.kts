plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// Generates the Android app's baseline profile and measures what it buys: UI Automator journeys through the app on a
// connected device (API 33+, or 28+ rooted). `./gradlew :app:androidApp:generateBaselineProfile` writes the profile
// into
// app/androidApp/src/release/generated/baselineProfiles/, where it is checked in. AGP 9 compiles the Kotlin itself.

android {
    namespace = "app.onlyshots.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app:androidApp"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// The phone on the desk, not a Gradle managed device: the journeys need its camera.
baselineProfile { useConnectedDevices = true }

// The app id the journeys start, read from the APK under test (Journeys.kt's targetPackage).
androidComponents {
    onVariants { variant ->
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { loader.load(it)?.applicationId ?: "" },
        )
    }
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
