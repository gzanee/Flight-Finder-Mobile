package it.gzanee.flightfinder.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Development preview: `./gradlew :app:run`, with -Dflightfinder.demo=true for the demo Google. */
fun main() = application {
    val demo = System.getProperty("flightfinder.demo") == "true"
    Window(onCloseRequest = ::exitApplication, title = "Flight Finder", state = rememberWindowState(size = DpSize(400.dp, 860.dp))) {
        App(if (demo) DemoHttp() else null)
    }
}
