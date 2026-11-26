package it.gzanee.flightfinder.ui.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import it.gzanee.flightfinder.ui.format.isItalian

/**
 * Messaggi del motore, in italiano come nel portale Python (i test li confrontano).
 * In inglese si traducono qui: ogni messaggio noto ha la sua risorsa, i gruppi tra
 * parentesi diventano gli argomenti. Un messaggio nuovo resta com'è.
 */
private class EngineMessage(pattern: String, @StringRes val id: Int) {
    val regex = Regex("^$pattern$")
}

private val MESSAGES = listOf(
    // Validazione della richiesta (Validation.kt, Planner.kt)
    EngineMessage("""(.+): scegli un valore fra (\d+) e (\d+)\.""", R.string.engine_value_range),
    EngineMessage("""Partenza: scegli da (\d+) a (\d+) aeroporti\.""", R.string.engine_origins_count),
    EngineMessage("""Destinazioni: scegli da (\d+) a (\d+) aeroporti\.""", R.string.engine_destinations_count),
    EngineMessage("""Partenza: uno dei codici aeroporto non esiste\.""", R.string.engine_origin_unknown),
    EngineMessage("""Destinazioni: uno dei codici aeroporto non esiste\.""", R.string.engine_destination_unknown),
    EngineMessage("""Periodo non valido: date future, massimo un anno\.""", R.string.engine_period),
    EngineMessage("""Seleziona almeno un giorno per departure_days\.""", R.string.engine_departure_days),
    EngineMessage("""Seleziona almeno un giorno per return_days\.""", R.string.engine_return_days),
    EngineMessage("""La fascia oraria deve stare nello stesso giorno\.""", R.string.engine_time_window),
    EngineMessage("""Il minimo di notti supera il massimo\.""", R.string.engine_nights),
    EngineMessage("""Massimo 9 viaggiatori\.""", R.string.engine_travelers),
    EngineMessage("""Serve un adulto per ogni neonato in braccio\.""", R.string.engine_infants),
    EngineMessage("""Il budget totale deve essere positivo e finito\.""", R.string.engine_budget),
    EngineMessage("""La durata minima supera quella massima\.""", R.string.engine_duration),
    EngineMessage("""Lo scalo minimo supera quello massimo\.""", R.string.engine_layover),
    EngineMessage("""Compagnie: usa codici IATA di due caratteri, es\. FR, V7\.""", R.string.engine_airlines),
    EngineMessage("""Gruppo di destinazioni non riconosciuto\.""", R.string.engine_destination_set),
    EngineMessage("""Per ripartire da un altro aeroporto scegli un gruppo di destinazioni esplicito\.""", R.string.engine_return_departure),
    EngineMessage("""Nessuna coppia di date rispetta giorni e notti scelti\.""", R.string.engine_no_date_pairs),
    EngineMessage("""Nessuna destinazione candidata disponibile\.""", R.string.engine_no_candidates),
    EngineMessage("""La ricerca genera ([\d.]+) combinazioni: massimo ([\d.]+)\. Riduci il periodo, le destinazioni o l'intervallo di notti\.""", R.string.engine_too_many),
    EngineMessage("""Riduci gli aeroporti o le date: troppi controlli di ritorno\.""", R.string.engine_too_many_returns),
    // Ricerche (SearchRunner.kt)
    EngineMessage("""Ricerca o risultato non trovato\.""", R.string.engine_not_found),
    EngineMessage("""Una ricerca è già in corso: fermala prima di avviarne un altra\.""", R.string.engine_already_running),
    EngineMessage("""Attendi che la ricerca attiva si fermi\.""", R.string.engine_wait_stop),
    EngineMessage("""Ricerca già completata\. Avviane una nuova per prezzi aggiornati\.""", R.string.engine_already_complete),
    EngineMessage("""Ferma la ricerca prima di eliminarla\.""", R.string.engine_stop_before_delete),
    EngineMessage("""Google vende i voli fino al (\S+): le date successive non sono state cercate\.""", R.string.engine_horizon),
    EngineMessage("""Ricerca interrotta; il progresso è salvato\.""", R.string.engine_interrupted),
    EngineMessage("""Ricerche saltate perché Google non ha risposto: (\d+)\. Qualche volo potrebbe mancare\.""", R.string.engine_skipped),
    EngineMessage("""Ricerca interrotta da un errore; il progresso è salvato\.""", R.string.engine_interrupted_error),
    EngineMessage("""App chiusa durante la ricerca: premi Riprendi per continuare\.""", R.string.engine_app_closed),
    EngineMessage("""Gli stessi voli non sono stati confermati con questi filtri\. Il risultato precedente non è più valido\.""", R.string.engine_recheck_not_confirmed),
    EngineMessage("""Il prezzo o gli orari non rispettano più i filtri\.""", R.string.engine_recheck_outside),
    // Google (GoogleFlightsClient.kt, GooglePageDecoder.kt)
    EngineMessage("""Google limita le richieste\. Ricerca fermata; riprova più tardi\.""", R.string.engine_google_limited),
    EngineMessage("""Google ha limitato le richieste\. Nessun tentativo automatico\.""", R.string.engine_google_blocked),
    EngineMessage("""Google ha risposto HTTP (\d+)\.""", R.string.engine_google_http),
    EngineMessage("""Connessione a Google non riuscita dopo vari tentativi\. Puoi premere Riprendi\.""", R.string.engine_google_connection),
    EngineMessage("""Pagina Google senza dati voli: consenso, blocco o formato cambiato\.""", R.string.engine_google_no_data),
    EngineMessage("""Google ha restituito un errore temporaneo anche dopo vari tentativi\. Puoi premere Riprendi\.""", R.string.engine_google_transient),
    EngineMessage("""Risposta Google non riconosciuta: non equivale a zero voli\.""", R.string.engine_google_unrecognized),
    // Prova connessione (SearchRepositoryImpl.testConnection)
    EngineMessage("""Risposta in (\d+) ms, (\d+) kB\.""", R.string.engine_test_response),
    EngineMessage("""Venezia → Barcellona, (\S+): (\d+) voli letti, (\d+) righe non verificabili\.""", R.string.engine_test_read),
    EngineMessage("""(\S+ \S+) alle (\S+): (\S+) €""", R.string.engine_test_flight),
)

/** Il messaggio nella lingua dell'app. */
@Composable
fun engineText(message: String): String {
    if (isItalian()) return message
    for (known in MESSAGES) {
        val match = known.regex.matchEntire(message) ?: continue
        return stringResource(known.id, *match.groupValues.drop(1).toTypedArray())
    }
    return message
}

/** La tratta in corso di ricerca ("Voli diretti · Venezia → Londra · 2026-12-18"), con le parole fisse tradotte. */
@Composable
fun engineProgress(text: String): String {
    if (isItalian()) return text
    val direct = stringResource(R.string.engine_progress_direct)
    val stops = stringResource(R.string.engine_progress_stops)
    val back = stringResource(R.string.engine_progress_return)
    val more = stringResource(R.string.engine_progress_more)
    return text
        .replace("Voli diretti · ", "$direct · ")
        .replace("Con scalo · ", "$stops · ")
        .replace("Ritorno: ", "$back: ")
        .replace(Regex(""" e altri (\d+)""")) { " " + more.format(it.groupValues[1]) }
}
