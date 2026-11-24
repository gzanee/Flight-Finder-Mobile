package it.gzanee.flightfinder.app.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual object Platform {
    @OptIn(ExperimentalForeignApi::class)
    actual fun dataDir(): Path {
        val url = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null) as NSURL
        return url.path!!.toPath()
    }

    actual fun httpClient() = HttpClient(Darwin) {
        install(HttpTimeout) { requestTimeoutMillis = 25_000 }
        expectSuccess = false
        engine {
            configureSession {
                // The consent cookie is sent by hand: the system cookie jar must not add others.
                setHTTPCookieStorage(null)
                setHTTPShouldSetCookies(false)
            }
        }
    }

    actual fun keepScreenOn(on: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = on
    }

    actual fun share(text: String) {
        val controller = UIActivityViewController(listOf(text), null)
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(controller, true, null)
    }

    actual val fileSystem: okio.FileSystem = okio.FileSystem.SYSTEM

    actual val name = "iOS"
}
