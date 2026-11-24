package it.gzanee.flightfinder.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.AppState
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.app.core.Platform
import it.gzanee.flightfinder.app.ui.AppCard
import it.gzanee.flightfinder.app.ui.Hint
import it.gzanee.flightfinder.app.ui.Notice
import it.gzanee.flightfinder.app.ui.PrimaryButton
import it.gzanee.flightfinder.app.ui.RowGap
import it.gzanee.flightfinder.app.ui.Title
import it.gzanee.flightfinder.app.ui.colors
import it.gzanee.flightfinder.engine.Calendar
import it.gzanee.flightfinder.engine.Trip
import kotlinx.coroutines.launch

/** One trip: flights, price freshness, recheck and the links to book on Google Flights. */
@Composable
fun TripScreen(jobId: String, tripId: String, onBack: () -> Unit) {
    val c = colors
    var trip by remember { mutableStateOf<Trip?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    LaunchedEffect(jobId, tripId) {
        trip = runCatching { AppState.jobs.trip(jobId, tripId) }.getOrNull()
        loaded = true
    }
    fun open(url: String?) = Fmt.safeLink(url)?.let { runCatching { uri.openUri(it) } }

    Screen("Viaggio", onBack) { padding ->
        val t = trip
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (t == null) {
                if (loaded) Notice(feedback?.second ?: "Questo viaggio non è più tra i risultati.")
                if (loaded) PrimaryButton("Torna ai risultati", kind = "secondary", onClick = onBack)
                return@Column
            }
            val e = ends(t)
            val primary = if (e.round) t.roundTripUrl ?: t.outbound.bookingUrl else t.outbound.bookingUrl
            Column {
                Title("${Fmt.city(e.origin)} → ${Fmt.flag(e.dest)} ${Fmt.city(e.dest)}")
                Hint(
                    (if (e.round) Fmt.count(t.nights ?: 0, "notte", "notti") else "Solo andata") +
                        (t.usefulMinutes?.takeIf { e.round }?.let { " · ${Fmt.duration(it)} ${if (e.dest == e.away) "a destinazione" else "tra i voli"}" } ?: "") +
                        (if (e.round && e.home != null && e.home != e.origin) " · rientro a ${Fmt.city(e.home)}" else ""),
                )
            }
            AppCard {
                Text(Fmt.money(t.price), color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Hint((if (e.round) "andata e ritorno, due biglietti separati" else "solo andata") + if (t.outbound.travelers > 1) ", ${t.outbound.travelers} persone" else "")
                Hint(if (t.needsRecheck == true) "Prezzo da aggiornare" else "Prezzo delle ${localTime(t.checkedAt)}", if (t.needsRecheck == true) "warn" else "muted")
            }
            feedback?.let { (tone, text) -> Notice(text, tone) }
            AppCard {
                Leg(t.outbound, "Andata")
                t.inbound?.let { Leg(it, "Ritorno") }
            }
            if (e.round && e.away != null && e.dest != e.away) {
                Notice("Arrivi a ${Fmt.airportName(e.dest)} e riparti da ${Fmt.airportName(e.away)}: il trasferimento è a carico tuo.")
            }
            t.roundTripFrom?.let {
                Notice("Con questa andata Google vende un biglietto A/R unico da ${Fmt.money(it)}, ma con un ritorno che può essere diverso da questo.", "info")
            }
            PrimaryButton("Vedi offerta su Google Flights", Modifier.fillMaxWidth(), enabled = Fmt.safeLink(primary) != null) { open(primary) }
            val inbound = t.inbound
            if (e.round && inbound != null) {
                RowGap {
                    PrimaryButton("Solo andata", Modifier.weight(1f), kind = "secondary") { open(t.outbound.bookingUrl) }
                    PrimaryButton("Solo ritorno", Modifier.weight(1f), kind = "secondary") { open(inbound.bookingUrl) }
                }
            }
            RowGap {
                PrimaryButton("Aggiorna prezzo", Modifier.weight(1f), kind = "secondary", loading = busy) {
                    busy = true
                    feedback = null
                    scope.launch {
                        try {
                            val check = AppState.jobs.recheck(jobId, t.id)
                            if (check.status == "confirmed") {
                                feedback = if (check.priceChanged == true) "warn" to "Il prezzo è cambiato: ora ${Fmt.money(check.result!!.price)}." else "good" to "Prezzo confermato."
                                trip = AppState.jobs.trip(jobId, t.id)
                            } else {
                                feedback = "bad" to (check.message ?: "")
                                trip = null
                            }
                        } catch (ex: Exception) {
                            feedback = "bad" to "Non è stato possibile aggiornare il prezzo: ${ex.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
                PrimaryButton("Condividi", Modifier.weight(1f), kind = "secondary") {
                    Platform.share(
                        listOfNotNull(
                            "${Fmt.city(e.origin)} → ${Fmt.city(e.dest)} · ${Fmt.money(t.price)}${if (e.round) " andata e ritorno" else ""}",
                            "Andata: ${Fmt.shortDay(t.outbound.first.departure)} ${Fmt.time(t.outbound.first.departure)}",
                            t.inbound?.let { "Ritorno: ${Fmt.shortDay(it.first.departure)} ${Fmt.time(it.first.departure)}" },
                            Fmt.safeLink(primary),
                        ).joinToString("\n"),
                    )
                }
            }
            Hint("Dati di shopping osservati su Google Flights: numero volo, aeroporti, orari e prezzo. Non sono una conferma della compagnia. Condizioni e bagagli vanno verificati dal venditore.")
        }
    }
}

private fun localTime(checkedAt: String) = Calendar.deviceClock(checkedAt)
