"""Testi dell'app in inglese e in italiano, in un posto solo.

Uso: python3 tools/strings.py
Scrive app/src/main/res/values/strings.xml (inglese, predefinito) e
values-it/strings.xml (italiano). Le due lingue hanno sempre le stesse chiavi.
"""
from pathlib import Path
from xml.sax.saxutils import escape

RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"

# chiave: (inglese, italiano). Gli argomenti sono %1$s, %2$d...
STRINGS = {
    "app_name": ("Flight Finder", "Flight Finder"),

    # ---- Messaggi del motore (ui/i18n/EngineText.kt): l'italiano non si usa, il motore lo produce già.
    "engine_value_range": ("%1$s: choose a value between %2$s and %3$s.", "%1$s: scegli un valore fra %2$s e %3$s."),
    "engine_origins_count": ("Departure: choose from %1$s to %2$s airports.", "Partenza: scegli da %1$s a %2$s aeroporti."),
    "engine_destinations_count": ("Destinations: choose from %1$s to %2$s airports.", "Destinazioni: scegli da %1$s a %2$s aeroporti."),
    "engine_origin_unknown": ("Departure: one of the airport codes does not exist.", "Partenza: uno dei codici aeroporto non esiste."),
    "engine_destination_unknown": ("Destinations: one of the airport codes does not exist.", "Destinazioni: uno dei codici aeroporto non esiste."),
    "engine_period": ("Invalid period: future dates only, up to one year ahead.", "Periodo non valido: date future, massimo un anno."),
    "engine_departure_days": ("Choose at least one departure day.", "Seleziona almeno un giorno di partenza."),
    "engine_return_days": ("Choose at least one return day.", "Seleziona almeno un giorno di ritorno."),
    "engine_time_window": ("The time window must stay within the same day.", "La fascia oraria deve stare nello stesso giorno."),
    "engine_nights": ("The minimum number of nights is higher than the maximum.", "Il minimo di notti supera il massimo."),
    "engine_travelers": ("Up to 9 travellers.", "Massimo 9 viaggiatori."),
    "engine_infants": ("Each infant on a lap needs an adult.", "Serve un adulto per ogni neonato in braccio."),
    "engine_budget": ("The total budget must be a positive number.", "Il budget totale deve essere positivo e finito."),
    "engine_duration": ("The minimum duration is longer than the maximum.", "La durata minima supera quella massima."),
    "engine_layover": ("The minimum layover is longer than the maximum.", "Lo scalo minimo supera quello massimo."),
    "engine_airlines": ("Airlines: use two-character IATA codes, e.g. FR, V7.", "Compagnie: usa codici IATA di due caratteri, es. FR, V7."),
    "engine_destination_set": ("Unknown destination group.", "Gruppo di destinazioni non riconosciuto."),
    "engine_return_departure": ("To fly back from another airport, choose an explicit destination group.", "Per ripartire da un altro aeroporto scegli un gruppo di destinazioni esplicito."),
    "engine_no_date_pairs": ("No date pair matches the days and nights you chose.", "Nessuna coppia di date rispetta giorni e notti scelti."),
    "engine_no_candidates": ("No candidate destination available.", "Nessuna destinazione candidata disponibile."),
    "engine_too_many": ("This search creates %1$s combinations, the maximum is %2$s. Shorten the period, the destinations or the range of nights.", "La ricerca genera %1$s combinazioni: massimo %2$s. Riduci il periodo, le destinazioni o l'intervallo di notti."),
    "engine_too_many_returns": ("Choose fewer airports or dates: too many return checks.", "Riduci gli aeroporti o le date: troppi controlli di ritorno."),
    "engine_not_found": ("Search or result not found.", "Ricerca o risultato non trovato."),
    "engine_already_running": ("A search is already running: stop it before starting another one.", "Una ricerca è già in corso: fermala prima di avviarne un altra."),
    "engine_wait_stop": ("Wait for the running search to stop.", "Attendi che la ricerca attiva si fermi."),
    "engine_already_complete": ("This search is complete. Start a new one for updated prices.", "Ricerca già completata. Avviane una nuova per prezzi aggiornati."),
    "engine_stop_before_delete": ("Stop the search before deleting it.", "Ferma la ricerca prima di eliminarla."),
    "engine_horizon": ("Google sells flights up to %1$s: later dates were not searched.", "Google vende i voli fino al %1$s: le date successive non sono state cercate."),
    "engine_interrupted": ("Search interrupted; progress is saved.", "Ricerca interrotta; il progresso è salvato."),
    "engine_interrupted_error": ("Search interrupted by an error; progress is saved.", "Ricerca interrotta da un errore; il progresso è salvato."),
    "engine_app_closed": ("The app was closed during the search: tap Resume to continue.", "App chiusa durante la ricerca: premi Riprendi per continuare."),
    "engine_recheck_not_confirmed": ("The same flights were not confirmed with these filters. The previous result is no longer valid.", "Gli stessi voli non sono stati confermati con questi filtri. Il risultato precedente non è più valido."),
    "engine_recheck_outside": ("The price or the times no longer match your filters.", "Il prezzo o gli orari non rispettano più i filtri."),
    "engine_google_limited": ("Google is limiting requests. Search stopped; try again later.", "Google limita le richieste. Ricerca fermata; riprova più tardi."),
    "engine_google_blocked": ("Google has limited requests. No automatic retry.", "Google ha limitato le richieste. Nessun tentativo automatico."),
    "engine_google_http": ("Google answered HTTP %1$s.", "Google ha risposto HTTP %1$s."),
    "engine_google_connection": ("Could not reach Google after several attempts. You can tap Resume.", "Connessione a Google non riuscita dopo vari tentativi. Puoi premere Riprendi."),
    "engine_google_no_data": ("Google page without flight data: consent, block or a changed format.", "Pagina Google senza dati voli: consenso, blocco o formato cambiato."),
    "engine_google_transient": ("Google returned a temporary error even after several attempts. You can tap Resume.", "Google ha restituito un errore temporaneo anche dopo vari tentativi. Puoi premere Riprendi."),
    "engine_google_unrecognized": ("Unrecognised Google answer: this does not mean zero flights.", "Risposta Google non riconosciuta: non equivale a zero voli."),
    "engine_progress_direct": ("Direct flights", "Voli diretti"),
    "engine_progress_stops": ("With a stop", "Con scalo"),
    "engine_progress_return": ("Return", "Ritorno"),
    "engine_progress_more": ("and %1$s more", "e altri %1$s"),

    # ---- Etichette dei tipi del motore (ui/i18n/Labels.kt)
    "seat_economy": ("Economy", "Economy"),
    "seat_premium": ("Premium economy", "Premium economy"),
    "seat_business": ("Business", "Business"),
    "seat_first": ("First", "Prima"),
    "baggage_base": ("Small bag only", "Solo bagaglio piccolo"),
    "baggage_cabin": ("With a cabin trolley", "Con trolley in cabina"),
    "baggage_checked": ("With a checked bag", "Con valigia in stiva"),
    "status_running": ("Running", "In corso"),
    "status_cancelling": ("Stopping", "In chiusura"),
    "status_cancelled": ("Stopped", "Fermata"),
    "status_blocked": ("Paused", "In pausa"),
    "status_interrupted": ("Interrupted", "Interrotta"),
    "status_complete": ("Complete", "Completata"),
    "sort_price": ("Lowest price", "Prezzo più basso"),
    "sort_useful": ("Most time there", "Più tempo a destinazione"),
    "sort_duration": ("Shortest flights", "Voli più brevi"),
    "sort_departure": ("Soonest departure", "Partenza più vicina"),
    "set_europe_short": ("Europe, short flights", "Europa, voli brevi"),
    "set_europe_wide": ("Europe, extended list", "Europa, elenco esteso"),
    "set_sun": ("Sun and sea", "Mare e sole"),
    "set_city_break": ("City breaks", "Weekend in città"),
    "set_long_haul": ("Long haul", "Lungo raggio"),
}

# chiave: ((inglese uno, inglese altri), (italiano uno, italiano altri)); %d è il numero.
PLURALS = {}

EXTRA = Path(__file__).with_name("strings_screens.py")
if EXTRA.exists():
    namespace = {}
    exec(EXTRA.read_text(encoding="utf-8"), namespace)
    for key in namespace["STRINGS"]:
        assert key not in STRINGS, key
    STRINGS.update(namespace["STRINGS"])
    PLURALS.update(namespace["PLURALS"])


def android(text: str) -> str:
    text = escape(text).replace("'", "\\'").replace('"', '\\"')
    return text


def write(folder: str, index: int) -> None:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generato da tools/strings.py: modificare lì. -->", "<resources>"]
    for key, values in STRINGS.items():
        lines.append(f'    <string name="{key}">{android(values[index])}</string>')
    for key, values in PLURALS.items():
        one, other = values[index]
        lines.append(f'    <plurals name="{key}">')
        lines.append(f'        <item quantity="one">{android(one)}</item>')
        lines.append(f'        <item quantity="other">{android(other)}</item>')
        lines.append("    </plurals>")
    lines.append("</resources>")
    path = RES / folder / "strings.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


write("values", 0)
write("values-it", 1)
print(len(STRINGS), "testi,", len(PLURALS), "plurali")
