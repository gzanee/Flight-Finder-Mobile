package it.gzanee.flightfinder.domain.repository

import it.gzanee.flightfinder.domain.model.SearchJob

/** Dove si salvano le ricerche (file sul telefono, memoria nei test). */
interface SearchStore {
    suspend fun load(id: String): SearchJob?
    suspend fun save(job: SearchJob)
    suspend fun list(): List<SearchJob>
    suspend fun remove(id: String)
}
