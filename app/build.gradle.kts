// The app's screens, written once in Compose Multiplatform for Android and iOS.
// The desktop (JVM) target is only for development: it compiles and shows the
// screens on a computer without Android Studio or Xcode.
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "it.gzanee.flightfinder.app"
        compileSdk = 36
        minSdk = 26
        androidResources.enable = true
    }

    val xcf = XCFramework("ComposeApp")
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            xcf.add(this)
        }
    }

    jvm("desktop")

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.okio)
            implementation(libs.ktor.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.okhttp)
            implementation(libs.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.compose.desktop)
                implementation(libs.ktor.java)
                implementation(libs.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.compose.ui.test)
            }
        }
    }
}

// The airport catalog of data/catalog becomes the app's resource files.
val catalogResources = layout.buildDirectory.dir("catalogResources")
val copyCatalog by tasks.registering(Sync::class) {
    from(rootProject.file("data/catalog")) { include("*.json") }
    into(catalogResources.map { it.dir("files") })
}

compose.resources {
    packageOfResClass = "it.gzanee.flightfinder.app.resources"
    customDirectory("commonMain", copyCatalog.map { catalogResources.get() })
}

tasks.withType<Test>().configureEach {
    // Screens of the UI test, to look at: app/build/screenshots.
    systemProperty("screenshots", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    systemProperty("flightfinder.data", layout.buildDirectory.dir("test-data").get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "it.gzanee.flightfinder.app.DesktopKt"
        jvmArgs += listOfNotNull(System.getProperty("flightfinder.demo")?.let { "-Dflightfinder.demo=$it" })
    }
}

// In UI tests the test thread is the main thread: without the Swing dispatcher,
// navigation does not expect Swing's event thread.
configurations.matching { it.name == "desktopTestRuntimeClasspath" }.configureEach {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
}
