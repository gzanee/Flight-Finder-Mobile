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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.AppState
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.app.ui.AppCard
import it.gzanee.flightfinder.app.ui.Hint
import it.gzanee.flightfinder.app.ui.Notice
import it.gzanee.flightfinder.app.ui.PrimaryButton
import it.gzanee.flightfinder.app.ui.colors
import it.gzanee.flightfinder.engine.Calendar
import it.gzanee.flightfinder.engine.bookingUrl
import it.gzanee.flightfinder.engine.decode
import it.gzanee.flightfinder.engine.oneWayQuery
import it.gzanee.flightfinder.engine.parseRequest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One real search, to check that Google answers this phone, with what came back. */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val c = colors
    var busy by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf<Boolean?>(null) }
    var lines by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    fun run() {
        busy = true
        ok = null
        scope.launch {
            val log = ArrayList<String>()
            val day = Calendar.addDays(Calendar.today(), 30)
            try {
                val r = parseRequest(buildJsonObject {
                    put("origins", JsonArray(listOf(JsonPrimitive("VCE")))); put("destinations", JsonArray(listOf(JsonPrimitive("BCN"))))
                    put("trip_type", "one_way"); put("date_mode", "fixed"); put("start_date", day); put("departure_min", "00:00"); put("max_stops", 1)
                })
                val q = oneWayQuery(listOf("VCE"), listOf("BCN"), day, r)
                val started = Calendar.nowSeconds()
                val html = AppState.live.fetchPage(q)
                log += "Risposta in ${((Calendar.nowSeconds() - started) * 1000).toInt()} ms, ${html.length / 1024} kB."
                val (quotes, rejected) = decode(html, r, bookingUrl(q), listOf("VCE") to listOf("BCN"))
                log += "Venezia → Barcellona, $day: ${quotes.size} voli letti, $rejected righe non verificabili."
                quotes.take(5).forEach { log += "${it.first.airlineCode} ${it.first.flightNumber} alle ${Fmt.time(it.first.departure)}: ${Fmt.money(it.price)}" }
                ok = true
            } catch (e: Exception) {
                log += "${e::class.simpleName}: ${e.message}"
                ok = false
            } finally {
                lines = log
                busy = false
            }
        }
    }

    Screen("Prova connessione", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Hint("Una sola ricerca reale, Venezia → Barcellona tra 30 giorni, dalla connessione di questo telefono. Serve a capire se Google risponde prima di avviare ricerche lunghe.")
            PrimaryButton("Prova adesso", Modifier.fillMaxWidth(), loading = busy) { run() }
            if (ok == true) Notice("Google risponde: le ricerche funzionano da questa rete.", "good")
            if (ok == false) Notice("La prova non è riuscita. Se Google limita le richieste, aspetta un quarto d’ora o prova con un’altra rete (Wi-Fi o dati mobili).", "bad")
            if (lines.isNotEmpty()) AppCard { lines.forEach { Text(it, color = c.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace) } }
        }
    }
}
