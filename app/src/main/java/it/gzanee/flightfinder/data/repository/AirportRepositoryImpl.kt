package it.gzanee.flightfinder.data.repository

import android.content.Context
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.search.AirportCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Il catalogo aeroporti dagli asset (data/catalog), caricato una volta sola fuori dal thread principale. */
class AirportRepositoryImpl(private val context: Context) : AirportRepository {
    private val mutex = Mutex()
    private var catalog: AirportCatalog? = null

    override suspend fun catalog(): AirportCatalog = mutex.withLock {
        catalog ?: withContext(Dispatchers.Default) {
            fun asset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
            AirportCatalog(asset("airports.json"), asset("places.json"), asset("routes.json"), asset("airlines.json"), asset("countries_it.json"), asset("cities_en.json"))
        }.also { catalog = it }
    }
}
