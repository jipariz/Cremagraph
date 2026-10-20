// Home: finding a machine on the network or a Bluetooth scale.
plugins { id("onlyshots.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sources)
            implementation(projects.feature.open)
            implementation(projects.storage)
            implementation(libs.ktor.client.core)
            implementation(libs.compose.ui.backhandler)
        }
    }
}
