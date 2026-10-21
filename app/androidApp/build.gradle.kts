import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
}

// The Android app: the activity, the manifest, launcher icons and the release build. Everything it shows is in
// :app:shared. AGP 9 compiles the Kotlin itself (built-in Kotlin), so there is no Kotlin plugin here.

dependencies {
    implementation(projects.app.shared)
    implementation(libs.androidx.activity.compose)
    // The root that exposes test tags as resource ids (MainActivity).
    implementation(libs.compose.foundation)
    // The system splash (Android 12+, backported below): the mark recording its shot, then the app's own hand-off.
    implementation(libs.androidx.splashscreen)
    // Installs the baseline profile shipped in the APK where the store has not compiled it already.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.app.baselineprofile)
}

// The release build compiles the code the journeys in :app:baselineprofile run ahead of time, and lays out the dex
// files for startup. The profile is checked in (src/release/generated/baselineProfiles/): a release build never needs
// a device.
baselineProfile {
    dexLayoutOptimization = true
    automaticGenerationDuringBuild = false
}

// ponytail: Compose's resource plugin never packages a KMP *library* module's resources into an
// Android artifact under AGP 9's KMP plugin — its copy task has no output directory wired, so the
// fonts were missing from the APK and the export crashed the first time it loaded
// one. Copy them into the app's assets at the path the runtime reader looks for. Upgrade path:
// delete this once the plugin wires copyAndroidMainComposeResourcesToAndroidAssets.
abstract class CopyLibraryComposeResources : DefaultTask() {
    @get:InputDirectory abstract val source: DirectoryProperty

    /** The assets root: the reader looks under composeResources/<resource package>/. */
    @get:Input abstract val resourcePackage: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun copy() {
        fs.sync {
            from(source)
            into(outputDir.get().dir("composeResources/" + resourcePackage.get()))
        }
    }
}

val coreComposeResources =
    tasks.register<CopyLibraryComposeResources>("copyCoreComposeResources") {
        source.set(
            project(":core:resources")
                .layout
                .buildDirectory
                .dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources")
        )
        resourcePackage.set("app.onlyshots.core.resources")
        outputDir.set(layout.buildDirectory.dir("generated/coreComposeResources"))
        dependsOn(":core:resources:prepareComposeResourcesTaskForCommonMain")
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            coreComposeResources,
            CopyLibraryComposeResources::outputDir,
        )
    }
}

android {
    namespace = "app.onlyshots"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        // Placeholder id until the store name and domain checks are done (open question).
        applicationId = "dev.parez.onlyshots"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // From onlyshots.version in gradle.properties (build-logic's AppVersion.kt).
        versionCode = appVersion.buildNumber
        versionName = appVersion.toString()
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            // On by the project's choice, which is why proguard-rules.pro exists and why the
            // release build gets its own smoke test: R8 breaks reflective paths silently.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The build types the baseline profile plugin adds for its journeys (nonMinifiedRelease, benchmarkRelease) copy the
// release one, which has no signing config: a debug key so they install on the phone. The store build is untouched.
android.buildTypes
    .matching { it.name.startsWith("nonMinified") || it.name.startsWith("benchmark") }
    .configureEach { signingConfig = android.signingConfigs.getByName("debug") }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
