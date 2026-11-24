package it.gzanee.flightfinder.app.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okio.Path
import okio.Path.Companion.toOkioPath
import java.lang.ref.WeakReference

actual object Platform {
    private lateinit var context: Context
    private var activity = WeakReference<Activity>(null)

    /** Called by MainActivity before showing the app. */
    fun attach(activity: Activity) {
        context = activity.applicationContext
        this.activity = WeakReference(activity)
    }

    actual fun dataDir(): Path = context.filesDir.toOkioPath()

    actual fun httpClient() = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 25_000 }
        expectSuccess = false
    }

    actual fun keepScreenOn(on: Boolean) {
        val window = activity.get()?.window ?: return
        activity.get()?.runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    actual fun share(text: String) {
        val a = activity.get() ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        a.startActivity(Intent.createChooser(send, null))
    }

    actual val fileSystem: okio.FileSystem = okio.FileSystem.SYSTEM

    actual val name = "Android"
}
