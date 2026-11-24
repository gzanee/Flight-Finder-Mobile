package it.gzanee.flightfinder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.gzanee.flightfinder.ui.screens.diagnostics.DiagnosticsScreen
import it.gzanee.flightfinder.ui.screens.history.HistoryScreen
import it.gzanee.flightfinder.ui.screens.place.PlaceScreen
import it.gzanee.flightfinder.ui.screens.results.ResultsScreen
import it.gzanee.flightfinder.ui.screens.search.PlaceKind
import it.gzanee.flightfinder.ui.screens.search.SearchScreen
import it.gzanee.flightfinder.ui.screens.search.SearchViewModel
import it.gzanee.flightfinder.ui.screens.trip.TripScreen

@Composable
fun AppNavigation(onToggleTheme: () -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Search.route) {

        // RICERCA
        composable(Screen.Search.route) {
            SearchScreen(
                viewModel = hiltViewModel(),
                onAddPlace = { kind -> navController.navigateSafe(Screen.Place.createRoute(kind)) },
                onSearchStarted = { id -> navController.navigateSafe(Screen.Results.createRoute(id)) },
                onHistory = { navController.navigateSafe(Screen.History.route) },
                onDiagnostics = { navController.navigateSafe(Screen.Diagnostics.route) },
                onToggleTheme = onToggleTheme,
            )
        }

        // AEROPORTI: aggiunge al modulo della schermata di ricerca
        composable(
            Screen.Place.route,
            arguments = listOf(navArgument(Screen.Place.ARG_KIND) { type = NavType.StringType }),
        ) { entry ->
            val searchEntry = remember(entry) { navController.getBackStackEntry(Screen.Search.route) }
            PlaceScreen(
                kind = PlaceKind.valueOf(entry.arguments?.getString(Screen.Place.ARG_KIND) ?: PlaceKind.DESTINATION.name),
                searchViewModel = hiltViewModel<SearchViewModel>(searchEntry),
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStackSafe() },
                onToggleTheme = onToggleTheme,
            )
        }

        // RISULTATI
        composable(
            Screen.Results.route,
            arguments = listOf(navArgument(Screen.Results.ARG_SEARCH_ID) { type = NavType.StringType }),
        ) { entry ->
            val searchEntry = remember(entry) { navController.getBackStackEntry(Screen.Search.route) }
            val searchViewModel = hiltViewModel<SearchViewModel>(searchEntry)
            ResultsScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStackSafe() },
                onTrip = { tripId -> navController.navigateSafe(Screen.Trip.createRoute(entry.arguments?.getString(Screen.Results.ARG_SEARCH_ID).orEmpty(), tripId)) },
                onEdit = { job ->
                    searchViewModel.edit(job.request)
                    navController.popBackStack(Screen.Search.route, inclusive = false)
                },
                onToggleTheme = onToggleTheme,
            )
        }

        // VIAGGIO
        composable(
            Screen.Trip.route,
            arguments = listOf(
                navArgument(Screen.Trip.ARG_SEARCH_ID) { type = NavType.StringType },
                navArgument(Screen.Trip.ARG_TRIP_ID) { type = NavType.StringType },
            ),
        ) {
            TripScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStackSafe() }, onToggleTheme = onToggleTheme)
        }

        // STORICO
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStackSafe() },
                onOpen = { id -> navController.navigateSafe(Screen.Results.createRoute(id)) },
                onToggleTheme = onToggleTheme,
            )
        }

        // PROVA CONNESSIONE
        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStackSafe() }, onToggleTheme = onToggleTheme)
        }
    }
}

/** Naviga solo dalla schermata visibile: un doppio tocco non apre due schermate. */
fun NavController.navigateSafe(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}

fun NavHostController.popBackStackSafe() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

sealed class Screen(val route: String) {
    object Search : Screen("ricerca")
    object Place : Screen("aeroporti/{kind}") {
        const val ARG_KIND = "kind"
        fun createRoute(kind: PlaceKind) = "aeroporti/${kind.name}"
    }
    object Results : Screen("risultati/{searchId}") {
        const val ARG_SEARCH_ID = "searchId"
        fun createRoute(searchId: String) = "risultati/$searchId"
    }
    object Trip : Screen("viaggio/{searchId}/{tripId}") {
        const val ARG_SEARCH_ID = "searchId"
        const val ARG_TRIP_ID = "tripId"
        fun createRoute(searchId: String, tripId: String) = "viaggio/$searchId/$tripId"
    }
    object History : Screen("storico")
    object Diagnostics : Screen("prova_connessione")
}
