plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
    alias(libs.plugins.kotlin.serialization)
}

// AppVersion (NAME, BUILD), generated from onlyshots.version so every platform shows the same version.
val generateAppVersion =
    tasks.register<GenerateAppVersion>("generateAppVersion") {
        versionName.set(appVersion.toString())
        buildNumber.set(appVersion.buildNumber)
        outputDir.set(layout.buildDirectory.dir("generated/appVersion/commonMain/kotlin"))
    }

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion.map { it.outputDir })
            dependencies { api(libs.kotlinx.serialization.json) }
        }
    }
}
