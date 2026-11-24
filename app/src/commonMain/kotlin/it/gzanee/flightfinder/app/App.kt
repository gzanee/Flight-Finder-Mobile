package it.gzanee.flightfinder.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import it.gzanee.flightfinder.app.core.AppState
import it.gzanee.flightfinder.app.screens.DiagnosticsScreen
import it.gzanee.flightfinder.app.screens.HistoryScreen
import it.gzanee.flightfinder.app.screens.PlaceScreen
import it.gzanee.flightfinder.app.screens.ResultsScreen
import it.gzanee.flightfinder.app.screens.SearchScreen
import it.gzanee.flightfinder.app.screens.TripScreen
import it.gzanee.flightfinder.app.ui.AppTheme
import it.gzanee.flightfinder.engine.Http
import kotlinx.serialization.Serializable

@Serializable object SearchRoute
@Serializable data class PlaceRoute(val kind: String)
@Serializable data class ResultsRoute(val id: String)
@Serializable data class TripRoute(val job: String, val trip: String)
@Serializable object HistoryRoute
@Serializable object DiagnosticsRoute

/** The whole app, shared by Android and iOS. `http` replaces the network in demos. */
@Composable
fun App(http: Http? = null) {
    AppTheme {
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            AppState.start(http)
            ready = true
        }
        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@AppTheme
        }
        val nav = rememberNavController()
        NavHost(nav, startDestination = SearchRoute) {
            composable<SearchRoute> {
                SearchScreen(
                    onPlace = { nav.navigate(PlaceRoute(it)) },
                    onResults = { nav.navigate(ResultsRoute(it)) },
                    onHistory = { nav.navigate(HistoryRoute) },
                    onDiagnostics = { nav.navigate(DiagnosticsRoute) },
                )
            }
            composable<PlaceRoute> { PlaceScreen(it.toRoute<PlaceRoute>().kind, onBack = { nav.popBackStack() }) }
            composable<ResultsRoute> {
                ResultsScreen(
                    it.toRoute<ResultsRoute>().id,
                    onBack = { nav.popBackStack() },
                    onTrip = { job, trip -> nav.navigate(TripRoute(job, trip)) },
                    onEdit = { nav.popBackStack(SearchRoute, inclusive = false) },
                )
            }
            composable<TripRoute> { val r = it.toRoute<TripRoute>(); TripScreen(r.job, r.trip, onBack = { nav.popBackStack() }) }
            composable<HistoryRoute> { HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(ResultsRoute(it)) }) }
            composable<DiagnosticsRoute> { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
