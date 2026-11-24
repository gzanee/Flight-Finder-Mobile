package it.gzanee.flightfinder.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.core.Fmt
import it.gzanee.flightfinder.app.ui.AppCard
import it.gzanee.flightfinder.app.ui.Chips
import it.gzanee.flightfinder.app.ui.Pill
import it.gzanee.flightfinder.app.ui.colors
import it.gzanee.flightfinder.engine.Quote
import it.gzanee.flightfinder.engine.Trip

/** One flight leg: times, airports, duration, stops and carriers. */
@Composable
fun Leg(quote: Quote, title: String) {
    val c = colors
    val first = quote.first
    val last = quote.last
    val stops = quote.segments.size - 1
    val airlines = quote.segments.map { it.airlineName.ifEmpty { it.airlineCode } }.distinct().joinToString(", ")
    val numbers = quote.segments.joinToString(" + ") { "${it.airlineCode} ${it.flightNumber}" }
    val nextDay = last.arrival.take(10) != first.departure.take(10)
    val via = quote.segments.dropLast(1).joinToString(", ") { Fmt.city(it.toCode) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.chip), contentAlignment = Alignment.Center) {
            Text(first.airlineName.ifEmpty { first.airlineCode }.take(1), color = c.muted, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$title · ${Fmt.shortDay(first.departure)}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Fmt.time(first.departure), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(first.fromCode, color = c.muted, fontSize = 13.sp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.duration(quote.durationMin), color = c.muted, fontSize = 11.sp)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    Text(
                        if (stops > 0) "${Fmt.count(stops, "scalo", "scali")} · $via" else "Diretto",
                        color = if (stops > 0) c.warn else c.good, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
                Text(Fmt.time(last.arrival) + if (nextDay) " +1" else "", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(last.toCode, color = c.muted, fontSize = 13.sp)
            }
            Text(
                "$airlines · $numbers" + if (stops > 0 && quote.selfTransfer == true) " · cambio volo a carico tuo" else "",
                color = c.muted, fontSize = 12.sp, maxLines = 2,
            )
        }
    }
}

data class TripEnds(val origin: String, val dest: String, val round: Boolean, val away: String?, val home: String?)

fun ends(t: Trip) = TripEnds(t.outbound.first.fromCode, t.outbound.last.toCode, t.inbound != null, t.inbound?.first?.fromCode, t.inbound?.last?.toCode)

@Composable
fun TripCard(trip: Trip, highlighted: Boolean, onClick: () -> Unit) {
    val c = colors
    val e = ends(trip)
    val tags = listOfNotNull(
        trip.usefulMinutes?.takeIf { e.round }?.let { "${Fmt.duration(it)} ${if (e.dest == e.away) "a destinazione" else "tra i voli"}" },
        "2 biglietti separati".takeIf { e.round },
        mapOf("cabin" to "Trolley incluso", "checked" to "Valigia inclusa")[trip.outbound.baggage],
        trip.roundTripFrom?.let { "A/R unico da ${Fmt.money(it)}" },
    )
    AppCard(onClick = onClick, highlighted = highlighted) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${Fmt.city(e.origin)} → ${Fmt.flag(e.dest)} ${Fmt.city(e.dest)}", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    (if (e.round) Fmt.count(trip.nights ?: 0, "notte", "notti") else "Solo andata") +
                        if (e.round && e.home != null && e.home != e.origin) " · rientro a ${Fmt.city(e.home)}" else "",
                    color = c.muted, fontSize = 13.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(trip.price), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        trip.needsRecheck == true -> "da aggiornare"
                        trip.outbound.travelers > 1 -> "${trip.outbound.travelers} persone"
                        e.round -> "A/R"
                        else -> "solo andata"
                    },
                    color = if (trip.needsRecheck == true) c.warn else c.muted, fontSize = 11.sp,
                )
            }
        }
        Leg(trip.outbound, "Andata")
        trip.inbound?.let { Leg(it, "Ritorno") }
        if (tags.isNotEmpty()) Chips { tags.forEach { Pill(it) } }
    }
}
