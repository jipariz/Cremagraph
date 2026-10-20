import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.nextMajor
import io.github.z4kn4fein.semver.nextMinor
import io.github.z4kn4fein.semver.nextPatch
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The root's version tasks. `bumpVersion` moves `onlyshots.version` on (`-Ppart=major|minor|patch`, or `-Pto=1.2.0`)
 * and writes it into the iOS project too, whose settings Xcode reads before any Gradle step runs.
 * `checkAppVersion` fails when the iOS project and `gradle.properties` disagree; CI runs it.
 */
class VersioningPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            require(this == rootProject) { "onlyshots.versioning belongs on the root project" }
            val properties = file("gradle.properties")
            // Read at configuration time as a provider: the tasks must not touch the Project while they run.
            val declared = providers.gradleProperty(VERSION_PROPERTY)
            val iosFiles = listOf(file("app/iosApp/project.yml"), file("app/iosApp/iosApp.xcodeproj/project.pbxproj"))

            tasks.register("bumpVersion") {
                group = "versioning"
                description = "Moves onlyshots.version on: -Ppart=major|minor|patch, or -Pto=X.Y.Z"
                val part = providers.gradleProperty("part")
                val to = providers.gradleProperty("to")
                doLast {
                    val current = parseAppVersion(declared.get())
                    val next: Version =
                        when {
                            to.isPresent -> parseAppVersion(to.get())
                            part.orNull == "major" -> current.nextMajor()
                            part.orNull == "minor" -> current.nextMinor()
                            part.orNull == "patch" -> current.nextPatch()
                            else -> throw GradleException("Pass -Ppart=major|minor|patch or -Pto=X.Y.Z")
                        }
                    if (next <= current) throw GradleException("$next is not after the current $current")
                    parseAppVersion(next.toString())
                    properties.writeText(
                        properties.readText().replace(Regex("(?m)^${Regex.escape(VERSION_PROPERTY)}=.*$"),
                            "$VERSION_PROPERTY=$next")
                    )
                    iosFiles.forEach { writeIosVersion(it, next) }
                    logger.lifecycle("onlyshots.version: $current → $next (build ${next.buildNumber})")
                }
            }

            tasks.register("checkAppVersion") {
                group = "versioning"
                description = "Fails when the iOS project's version differs from onlyshots.version"
                doLast {
                    val version = parseAppVersion(declared.get())
                    val wrong = iosFiles.filter { readIosVersion(it) != version.toString() to version.buildNumber.toString() }
                    if (wrong.isNotEmpty()) {
                        throw GradleException(
                            "${wrong.joinToString { it.name }} disagree with $VERSION_PROPERTY=$version " +
                                "(build ${version.buildNumber}); run ./gradlew bumpVersion -Pto=$version or fix them"
                        )
                    }
                    logger.lifecycle("onlyshots.version $version (build ${version.buildNumber}) matches the iOS project")
                }
            }
        }
    }
}

private val marketing = Regex("""(MARKETING_VERSION\s*[:=]\s*"?)([^";\n]*)("?)""")
private val build = Regex("""(CURRENT_PROJECT_VERSION\s*[:=]\s*"?)([^";\n]*)("?)""")

/** The iOS file's version and build number; every occurrence must agree, or the pair is null. */
private fun readIosVersion(file: File): Pair<String, String>? {
    val text = file.readText()
    val versions = marketing.findAll(text).map { it.groupValues[2].trim() }.toSet()
    val builds = build.findAll(text).map { it.groupValues[2].trim() }.toSet()
    return if (versions.size == 1 && builds.size == 1) versions.single() to builds.single() else null
}

private fun writeIosVersion(file: File, version: Version) {
    val text =
        file
            .readText()
            .replace(marketing) { "${it.groupValues[1]}$version${it.groupValues[3]}" }
            .replace(build) { "${it.groupValues[1]}${version.buildNumber}${it.groupValues[3]}" }
    file.writeText(text)
}
