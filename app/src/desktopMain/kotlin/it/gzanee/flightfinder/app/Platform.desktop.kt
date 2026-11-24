package it.gzanee.flightfinder.app.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import okio.Path
import okio.Path.Companion.toPath

/** Development only: the screens on a computer. */
actual object Platform {
    actual fun dataDir(): Path = (System.getProperty("flightfinder.data") ?: (System.getProperty("user.home") + "/.flight-finder-mobile")).toPath()

    actual fun httpClient() = HttpClient(Java) {
        install(HttpTimeout) { requestTimeoutMillis = 25_000 }
        expectSuccess = false
    }

    actual fun keepScreenOn(on: Boolean) = Unit

    actual fun share(text: String) = println(text)

    actual val fileSystem: okio.FileSystem = okio.FileSystem.SYSTEM

    actual val name = "Desktop"
}
