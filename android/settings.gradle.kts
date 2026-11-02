pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}
rootProject.name = "Thirteen"
include(":app")
// The battle protocol and rooms, shared with the server.
include(":protocol")
project(":protocol").projectDir = file("../protocol")
