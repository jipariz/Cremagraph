import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds the browser targets, for the modules the web editor is built from: wasm, and Kotlin/JS for browsers without
 * WasmGC (the web app ships both, see `composeCompatibilityBrowserDistribution`). Shared web code lives in `webMain`.
 */
class KmpWasmConventionPlugin : Plugin<Project> {
    @OptIn(ExperimentalWasmDsl::class)
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            extensions.configure<KotlinMultiplatformExtension> {
                js { browser() }
                wasmJs { browser() }
            }
        }
}
