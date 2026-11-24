rootProject.name = "flight-finder-mobile"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// engine: search engine (no UI). app: Compose Multiplatform UI shared by Android and iOS.
// androidApp: the Android application. iosApp/ is the Xcode project.
include(":engine", ":app", ":androidApp")
