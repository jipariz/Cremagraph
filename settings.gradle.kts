rootProject.name = "OnlyShots"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  // Convention plugins shared by the module build files.
  includeBuild("build-logic")
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  // PREFER_SETTINGS, not FAIL_ON_PROJECT_REPOS: the wasm toolchain adds its own repositories for
  // Node, Yarn and Binaryen from inside the Kotlin plugin, which the stricter mode refuses
  // outright. They are declared here instead, and anything else a module adds is ignored.
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    ivy("https://nodejs.org/dist") {
      name = "Node Distributions"
      patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
      metadataSources { artifact() }
      content { includeModule("org.nodejs", "node") }
    }
    ivy("https://github.com/yarnpkg/yarn/releases/download") {
      name = "Yarn Distributions"
      patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
      metadataSources { artifact() }
      content { includeModule("com.yarnpkg", "yarn") }
    }
    ivy("https://github.com/WebAssembly/binaryen/releases/download") {
      name = "Binaryen Distributions"
      patternLayout {
        artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]")
      }
      metadataSources { artifact() }
      content { includeModule("com.github.webassembly", "binaryen") }
    }
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
  }
}

include(
    ":app:shared",
    ":app:androidApp",
    ":app:baselineprofile",
    ":app:desktopApp",
    ":core:ui",
    ":feature:style",
    ":feature:edit",
    ":feature:open",
    ":feature:record",
    ":feature:home",
    ":editor",
    ":core:designsystem",
    ":core:resources",
    ":core:model",
    ":overlay",
    ":overlay:compose",
    ":sources",
    ":camera",
    ":video",
    ":storage",
    ":screenshots",
    ":app:webApp",
    ":uitests",
    ":app:uitests",
)
