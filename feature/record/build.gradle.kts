// Recording: the camera, the live graph, auto-start and the stop rules.
plugins { id("onlyshots.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.edit)
            implementation(projects.storage)
            implementation(projects.sources)
            implementation(projects.camera)
            implementation(libs.kermit)
        }
    }
}
