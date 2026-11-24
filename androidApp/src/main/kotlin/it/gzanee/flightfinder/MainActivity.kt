package it.gzanee.flightfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.gzanee.flightfinder.app.App
import it.gzanee.flightfinder.app.core.Platform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Platform.attach(this)
        setContent { App() }
    }
}
