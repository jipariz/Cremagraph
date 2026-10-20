import org.gradle.api.Plugin
import org.gradle.api.Project

/** Compose Multiplatform with the Kotlin Compose compiler. */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")
        }
}
