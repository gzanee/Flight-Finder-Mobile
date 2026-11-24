@file:UseSerializers(LocalDateSerializer::class, InstantSerializer::class)

package it.gzanee.flightfinder.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate

@Serializable
enum class JobStatus(val label: String) {
    @SerialName("running") RUNNING("In corso"),
    @SerialName("cancelling") CANCELLING("In chiusura"),
    @SerialName("cancelled") CANCELLED("Fermata"),
    @SerialName("complete") COMPLETE("Completata"),
    @SerialName("blocked") BLOCKED("In pausa"),
    @SerialName("interrupted") INTERRUPTED("Interrotta"),
    ;

    val isRunning get() = this == RUNNING || this == CANCELLING
    val canResume get() = this == CANCELLED || this == BLOCKED || this == INTERRUPTED
}

/** Esito di "Aggiorna prezzo" su un viaggio. */
@Serializable
sealed interface RecheckResult {
    @Serializable @SerialName("confirmed")
    data class Confirmed(val result: Trip, val priceChanged: Boolean) : RecheckResult

    @Serializable @SerialName("not_confirmed")
    data class NotConfirmed(val message: String) : RecheckResult

    @Serializable @SerialName("outside_filters")
    data class OutsideFilters(val message: String) : RecheckResult
}

/** Posizione nel calendario di una ricerca: giorno, fase (andate/ritorni), gruppo di aeroporti. */
@Serializable
data class Cursor(val day: Int = 0, val stage: Int = 0, val batch: Int = 0)

/**
 * Una ricerca salvata sul telefono: richiesta, avanzamento (riprendibile) e
 * viaggi trovati. Port di un "job" di flightcore/flexible_jobs.py.
 */
@Serializable
data class SearchJob(
    val id: String,
    val request: SearchRequest,
    val plan: Plan,
    val status: JobStatus,
    val completed: Int = 0,
    val total: Int = 0,
    val cursor: Cursor = Cursor(),
    val pass: Int = 0,
    /** Andate ancora in attesa del ritorno, per "origine|destinazione|giorno". */
    val outbound: Map<String, List<Quote>> = emptyMap(),
    val requests: Int = 0,
    val returnDays: Int = 0,
    val returnRequests: Int = 0,
    val etaSeconds: Int? = null,
    val results: Map<String, Trip> = emptyMap(),
    val rechecks: Map<String, RecheckResult> = emptyMap(),
    /** Prezzi del biglietto A/R unico, per tratta e date: impronta dell'andata -> prezzo. */
    val roundTrips: Map<String, Map<String, Double>> = emptyMap(),
    val unverified: Int = 0,
    val emptySearches: Int = 0,
    val issues: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val current: String? = null,
    /** Giorno della ricerca: Google vende i voli fino a 330 giorni da oggi. */
    val today: LocalDate,
)
