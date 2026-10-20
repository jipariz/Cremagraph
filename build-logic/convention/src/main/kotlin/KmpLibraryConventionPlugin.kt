import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * A Kotlin Multiplatform library on the app's targets: Android (AGP 9's KMP library plugin, host tests on), iOS device
 * and simulator, and the desktop JVM. The Android namespace follows the project path (`:feature:edit` →
 * `app.onlyshots.feature.edit`); every JVM compilation targets Java 17.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            extensions.configure<KotlinMultiplatformExtension> {
                extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                    namespace = "app.onlyshots" + path.replace(':', '.')
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    withHostTest {}
                }
                iosArm64()
                iosSimulatorArm64()
                jvm("desktop")
                sourceSets.getByName("commonTest").dependencies { implementation(kotlin("test")) }
            }
            tasks.withType<KotlinJvmCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
        }
}
