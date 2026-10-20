import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/** The Gradle property in the root `gradle.properties` that holds the app's one version. */
internal const val VERSION_PROPERTY = "onlyshots.version"

/**
 * The app's version, `onlyshots.version` in the root `gradle.properties`, parsed strictly as semver: every platform's
 * version comes from it (Android's name and code, iOS's settings via `bumpVersion`, the desktop package, and the
 * generated `AppVersion` the screens show). A malformed or pre-release value fails the build.
 */
val Project.appVersion: Version
    get() {
        val raw =
            rootProject.findProperty(VERSION_PROPERTY)?.toString()
                ?: error("$VERSION_PROPERTY is missing from gradle.properties")
        return parseAppVersion(raw)
    }

internal fun parseAppVersion(raw: String): Version {
    val version =
        runCatching { raw.trim().toVersion(strict = true) }
            .getOrElse { throw IllegalArgumentException("$VERSION_PROPERTY=$raw is not a version: ${it.message}") }
    require(!version.isPreRelease && version.buildMetadata == null) {
        "$VERSION_PROPERTY=$raw: the app's version is a plain MAJOR.MINOR.PATCH"
    }
    require(version.minor < 100 && version.patch < 100) {
        "$VERSION_PROPERTY=$raw: minor and patch stay under 100, so the build number keeps increasing"
    }
    return version
}

/**
 * The build number the stores want (Android's versionCode, iOS's CFBundleVersion): `major × 10000 + minor × 100 +
 * patch`, so 1.2.3 is 10203. It grows with every version, which is why uploading the same version twice needs a bump.
 */
val Version.buildNumber: Int
    get() = major * 10_000 + minor * 100 + patch

/** Writes `app.onlyshots.core.model.AppVersion`, the version as the screens show it, into a generated source dir. */
abstract class GenerateAppVersion : DefaultTask() {
    @get:Input abstract val versionName: Property<String>

    @get:Input abstract val buildNumber: Property<Int>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.get().file("app/onlyshots/core/model/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated from onlyshots.version in gradle.properties (GenerateAppVersion). Do not edit.
            |package app.onlyshots.core.model
            |
            |/** The app's version, the same on every platform. */
            |object AppVersion {
            |    /** As the user reads it: MAJOR.MINOR.PATCH. */
            |    const val NAME: String = "${versionName.get()}"
            |
            |    /** The stores' build number for it. */
            |    const val BUILD: Int = ${buildNumber.get()}
            |}
            |"""
                .trimMargin()
        )
    }
}
