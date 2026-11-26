package it.gzanee.flightfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import it.gzanee.flightfinder.service.SearchService
import it.gzanee.flightfinder.ui.MainViewModel
import it.gzanee.flightfinder.ui.navigation.AppNavigation
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme
import it.gzanee.flightfinder.ui.theme.FlightFinderTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** La ricerca da aprire quando si tocca una notifica. */
    private var openSearchId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openSearchId = intent?.getStringExtra(EXTRA_SEARCH_ID)

        setContent {
            val chosenTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val isDarkTheme = chosenTheme ?: isSystemInDarkTheme()
            val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
            val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                // Anche senza permesso il servizio funziona: la notifica semplicemente non si vede.
                SearchService.start(this)
            }
            // Una ricerca è partita o ripresa (l'app è in primo piano): parte il servizio che la tiene viva in background.
            LaunchedEffect(isSearching) {
                if (!isSearching) return@LaunchedEffect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    SearchService.start(this@MainActivity)
                }
            }

            FlightFinderTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FlightFinderColorScheme.colors.background,
                ) {
                    AppNavigation(
                        onToggleTheme = { viewModel.toggleTheme(isDarkTheme) },
                        openSearchId = openSearchId,
                        onSearchOpened = { openSearchId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SEARCH_ID)?.let { openSearchId = it }
    }

    companion object {
        const val EXTRA_SEARCH_ID = "searchId"
    }
}
