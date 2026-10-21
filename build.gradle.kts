plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.compose.hot.reload) apply false
  alias(libs.plugins.sqldelight) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.mugshot) apply false
  alias(libs.plugins.ktfmt)
  // bumpVersion and checkAppVersion: the app's version
  // (build-logic/convention/.../VersioningPlugin.kt).
  alias(libs.plugins.onlyshots.versioning)
}

// One formatter for every module and every source set, so "what does this look like" is never a
// review comment. kotlinLangStyle is the 4-space one the codebase was already written in.
subprojects {
  apply(plugin = "com.ncorti.ktfmt.gradle")
  extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
    kotlinLangStyle()
    maxWidth.set(120)
  }
}
