// Convention plugins: what the module build files would otherwise repeat (targets, SDK levels, JVM level, Compose,
// the dependencies every feature module takes). Included from the root settings' pluginManagement.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

rootProject.name = "build-logic"

include(":convention")
