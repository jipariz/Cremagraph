import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Screenshot tests. Every `@Mugshot @Preview` here is rendered on the JVM with layoutlib and compared with a golden
 * image; no emulator, no device. See the README for record and verify.
 *
 * Nothing depends on this module, so no preview code ships in the app.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mugshot)
}

kotlin {
    androidLibrary {
        namespace = "app.onlyshots.screenshots"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
        // Mugshot's generated tests live in androidHostTest and need the resources.
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            implementation(projects.core.model)
            implementation(projects.overlay.compose)
            // Whole screens, through their stateless bodies.
            implementation(projects.core.ui)
            implementation(projects.feature.home)
            implementation(projects.feature.record)
            implementation(projects.feature.edit)
            implementation(projects.feature.style)
            implementation(projects.feature.open)
            implementation(projects.sources)
            implementation(projects.camera)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui.tooling.preview)
        }
        androidMain.dependencies { implementation(libs.compose.ui.tooling) }
    }
}

// Compose resources of a library module are not packaged for host tests under AGP 9's KMP plugin (the same gap
// androidApp works around for the APK). The reader falls back to the classpath, so put :core:resources' prepared
// resources there at the path it looks for: composeResources/<resource package>/.
val coreComposeResources =
    tasks.register<Sync>("syncCoreComposeResources") {
        from(
            project(":core:resources")
                .layout
                .buildDirectory
                .dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources")
        )
        into(layout.buildDirectory.dir("generated/coreComposeResources/composeResources/app.onlyshots.core.resources"))
        dependsOn(":core:resources:prepareComposeResourcesTaskForCommonMain")
    }

kotlin.sourceSets
    .getByName("androidHostTest")
    .resources
    .srcDir(files(layout.buildDirectory.dir("generated/coreComposeResources")).builtBy(coreComposeResources))
