plugins {
    id("onlyshots.kmp.library")
    id("onlyshots.kmp.wasm")
    id("onlyshots.compose")
}

// Every font, drawable and user-visible string of the app, as Compose resources (src/commonMain/composeResources).
// Strings live in values/strings_<area>.xml; code outside composition carries them as UiText.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.components.resources)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "app.onlyshots.core.resources"
}
