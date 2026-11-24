package it.gzanee.flightfinder.ui.screens.components

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
import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.ui.format.asDuration
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.clock
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.shortDay
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

/** Nome della città e bandiera di un aeroporto, dal catalogo. */
fun interface PlaceNames {
    fun city(code: String): String
    fun flag(code: String): String = ""
}

/** Una tratta: orari, aeroporti, durata, scali e compagnie. */
@Composable
fun LegRow(quote: Quote, title: String, names: PlaceNames) {
    val first = quote.first
    val last = quote.last
    val airlines = quote.segments.map { it.airlineName.ifEmpty { it.airlineCode } }.distinct().joinToString(", ")
    val numbers = quote.segments.joinToString(" + ") { "${it.airlineCode} ${it.flightNumber}" }
    val nextDay = last.arrival.toLocalDate() != first.departure.toLocalDate()
    val via = quote.segments.dropLast(1).joinToString(", ") { names.city(it.toCode) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(first.airlineName.ifEmpty { first.airlineCode }.take(1), color = colors.textSecondary, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$title · ${first.departure.shortDay()}", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(first.departure.clock(), color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(first.fromCode, color = colors.textSecondary, fontSize = 13.sp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(quote.durationMin.asDuration(), color = colors.textSecondary, fontSize = 11.sp)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    Text(
                        if (quote.stops > 0) "${count(quote.stops, "scalo", "scali")} · $via" else "Diretto",
                        color = if (quote.stops > 0) colors.warn else colors.good, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
                Text(last.arrival.clock() + if (nextDay) " +1" else "", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(last.toCode, color = colors.textSecondary, fontSize = 13.sp)
            }
            Text(
                "$airlines · $numbers" + if (quote.stops > 0 && quote.selfTransfer == true) " · cambio volo a carico tuo" else "",
                color = colors.textSecondary, fontSize = 12.sp, maxLines = 2,
            )
        }
    }
}

/** Un viaggio nella lista dei risultati. */
@Composable
fun TripCard(trip: Trip, names: PlaceNames, highlighted: Boolean = false, onClick: () -> Unit) {
    val inbound = trip.inbound
    val away = inbound?.first?.fromCode
    val home = inbound?.last?.toCode
    val tags = listOfNotNull(
        trip.usefulMinutes?.takeIf { inbound != null }?.let { "${it.asDuration()} ${if (trip.destination == away) "a destinazione" else "tra i voli"}" },
        "2 biglietti separati".takeIf { inbound != null },
        when (trip.outbound.baggage) {
            Baggage.CABIN -> "Trolley incluso"
            Baggage.CHECKED -> "Valigia inclusa"
            Baggage.BASE -> null
        },
        trip.roundTripFrom?.let { "A/R unico da ${it.asEuro()}" },
    )
    FlightFinderCard(onClick = onClick, highlighted = highlighted) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${names.city(trip.origin)} → ${names.flag(trip.destination)} ${names.city(trip.destination)}", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    (if (inbound != null) count(trip.nights ?: 0, "notte", "notti") else "Solo andata") +
                        if (home != null && home != trip.origin) " · rientro a ${names.city(home)}" else "",
                    color = colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(trip.price.asEuro(), color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        trip.needsRecheck == true -> "da aggiornare"
                        trip.outbound.travelers > 1 -> "${trip.outbound.travelers} persone"
                        inbound != null -> "A/R"
                        else -> "solo andata"
                    },
                    color = if (trip.needsRecheck == true) colors.warn else colors.textSecondary, fontSize = 11.sp,
                )
            }
        }
        LegRow(trip.outbound, "Andata", names)
        if (inbound != null) LegRow(inbound, "Ritorno", names)
        if (tags.isNotEmpty()) ChipGroup { tags.forEach { Tag(it) } }
    }
}
