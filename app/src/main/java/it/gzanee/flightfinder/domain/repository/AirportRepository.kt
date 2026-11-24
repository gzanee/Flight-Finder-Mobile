package it.gzanee.flightfinder.domain.repository

import it.gzanee.flightfinder.domain.search.AirportCatalog

/** Il catalogo aeroporti incluso nell'app, caricato una volta sola. */
interface AirportRepository {
    suspend fun catalog(): AirportCatalog
}
