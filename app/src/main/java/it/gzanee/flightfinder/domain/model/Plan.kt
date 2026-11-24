package it.gzanee.flightfinder.domain.model

import kotlinx.serialization.Serializable

/** Il lavoro di una ricerca, mostrato prima di avviarla. */
@Serializable
data class Plan(
    val datePairs: List<DatePair>,
    val destinations: List<String>,
    val candidateCount: Int,
    val combinations: Int,
    val distinctSearches: Int,
    val searchesUpperBound: Int,
    val airportsPerSearch: Int,
    val directFirst: Boolean,
    @Serializable(with = LocalDateSerializer::class) val unsoldFrom: java.time.LocalDate?,
    val coverage: String,
    val originRouteCounts: Map<String, Int>,
    val notice: String,
)
