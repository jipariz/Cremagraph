import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature module: a Compose KMP library on the shared UI plumbing (`:core:ui`), with view models, Koin and the test
 * helpers every feature uses. A feature's own build file adds only what is particular to it.
 */
class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("onlyshots.kmp.library")
            pluginManager.apply("onlyshots.compose")
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    api(project(":core:ui"))
                    implementation(libs.library("androidx-lifecycle-viewmodel-compose"))
                    implementation(libs.library("androidx-lifecycle-runtime-compose"))
                    implementation(libs.library("koin-core"))
                    implementation(libs.library("koin-compose-viewmodel"))
                    // decodeToImageBitmap, for thumbnails and frames of the user's videos.
                    implementation(libs.library("compose-components-resources"))
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.library("kotlinx-coroutines-test"))
                    implementation(libs.library("okio-fakefilesystem"))
                }
            }
        }
}
