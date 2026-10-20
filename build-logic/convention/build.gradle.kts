plugins { `kotlin-dsl` }

// compileOnly: the root build puts these plugins on the classpath (its `apply false`), so the convention plugins use
// that one copy rather than bringing a second.
dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    implementation(libs.semver)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "onlyshots.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpWasm") {
            id = "onlyshots.kmp.wasm"
            implementationClass = "KmpWasmConventionPlugin"
        }
        register("compose") {
            id = "onlyshots.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("feature") {
            id = "onlyshots.feature"
            implementationClass = "FeatureConventionPlugin"
        }
        register("versioning") {
            id = "onlyshots.versioning"
            implementationClass = "VersioningPlugin"
        }
    }
}
