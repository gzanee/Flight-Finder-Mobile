package it.gzanee.flightfinder.domain.repository

import it.gzanee.flightfinder.domain.model.Plan
import it.gzanee.flightfinder.domain.model.RecheckResult
import it.gzanee.flightfinder.domain.model.ResultFilters
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.SortOrder
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.domain.search.SearchView
import kotlinx.coroutines.flow.Flow

/** Una ricerca validata con il suo piano, pronta da avviare. */
data class PlannedSearch(val request: SearchRequest, val plan: Plan, val estimatedMinutes: Int)

/** Esito di "Prova la connessione a Google". */
data class ConnectionTest(val ok: Boolean, val lines: List<String>)

interface SearchRepository {
    /** Ogni stato salvato di una ricerca, mentre gira. */
    val updates: Flow<SearchJob>

    /** La ricerca in corso, se c'è. */
    val activeId: String?

    /** Valida la richiesta e calcola il lavoro. Lancia SearchValidationError. */
    suspend fun plan(request: SearchRequest): PlannedSearch

    suspend fun start(search: PlannedSearch): String
    suspend fun resume(id: String)
    suspend fun cancel(id: String)
    suspend fun remove(id: String)
    suspend fun all(): List<SearchJob>
    suspend fun view(id: String, sort: SortOrder, offset: Int, filters: ResultFilters, focus: String?): SearchView
    suspend fun trip(id: String, tripId: String): Trip?
    suspend fun recheck(id: String, tripId: String): RecheckResult
    suspend fun testConnection(): ConnectionTest
}
