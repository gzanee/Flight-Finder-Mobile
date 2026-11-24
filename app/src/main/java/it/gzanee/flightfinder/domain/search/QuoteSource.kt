package it.gzanee.flightfinder.domain.search

import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.SearchRequest
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Le offerte di una tratta: quelle lette, le righe non verificabili, e se non c'era nessun volo. */
data class RouteQuotes(val quotes: List<Quote>, val unverified: Int, val empty: Boolean)

/** La fonte dei prezzi non ha risposto con dati utilizzabili: non equivale a "nessun volo". */
open class QuotesUnavailableException(message: String) : Exception(message)

/** La fonte limita le richieste: la ricerca si ferma. */
open class QuotesBlockedException(message: String) : QuotesUnavailableException(message)

/** Da dove arrivano i prezzi live (Google Flights nell'app). */
interface QuoteSource {
    suspend fun searchMany(origins: List<String>, destinations: List<String>, day: LocalDate, request: SearchRequest, inbound: Boolean = false, fresh: Boolean = false): Map<Route, RouteQuotes>

    suspend fun search(origin: String, destination: String, day: LocalDate, request: SearchRequest, inbound: Boolean = false, fresh: Boolean = false): RouteQuotes =
        searchMany(listOf(origin), listOf(destination), day, request, inbound, fresh).getValue(origin to destination)

    /** Prezzi del biglietto A/R unico: impronta dell'andata → prezzo, o null se non disponibili. */
    suspend fun roundTripFares(origin: String, destination: String, out: LocalDate, back: LocalDate, request: SearchRequest): Map<String, Double>?

    /** Link a Google Flights per lo stesso viaggio come un'unica ricerca A/R. */
    fun roundTripUrl(trip: it.gzanee.flightfinder.domain.model.Trip, request: SearchRequest): String?

    /** Secondi stimati per una richiesta, per il tempo che manca. */
    fun secondsPerSearch(request: SearchRequest): Double
}

/** Orologio in secondi e pause: sostituibile nei test. */
interface EngineClock {
    fun now(): Double
    suspend fun sleep(seconds: Double)

    object System : EngineClock {
        override fun now() = java.lang.System.currentTimeMillis() / 1000.0
        override suspend fun sleep(seconds: Double) = delay((seconds * 1000).toLong())
    }
}
