package it.gzanee.flightfinder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import it.gzanee.flightfinder.ui.MainViewModel
import it.gzanee.flightfinder.ui.navigation.AppNavigation
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val chosenTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val isDarkTheme = chosenTheme ?: isSystemInDarkTheme()
            val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
            LaunchedEffect(isSearching) {
                if (isSearching) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            FlightFinderTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FlightFinderColorScheme.colors.background,
                ) {
                    AppNavigation(onToggleTheme = { viewModel.toggleTheme(isDarkTheme) })
                }
            }
        }
    }
}
