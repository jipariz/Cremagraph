plugins { alias(libs.plugins.android.test) }

// End-to-end smoke tests of the installed Android app: UI Automator drives the debug build on an emulator or a phone,
// finding controls by their test tags (app.onlyshots.TestTags, exposed as resource ids by MainActivity), so a redesign
// can't break them by wording or looks.
//
//   ./gradlew :app:uitests:connectedAndroidTest
android {
    namespace = "app.onlyshots.uitests.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app:androidApp"
    // Runs in its own process and drives the app from outside, as a user would (UI Automator), rather than inside the
    // app's process, where its own copies of AndroidX would clash with the app's.
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// The app id the tests start, read from the APK under test (AppSmokeTest's app).
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
}
