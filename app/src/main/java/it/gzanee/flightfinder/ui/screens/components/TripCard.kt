package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.domain.model.Baggage
import it.gzanee.flightfinder.domain.model.Quote
import it.gzanee.flightfinder.domain.model.Trip
import it.gzanee.flightfinder.ui.format.asDuration
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.clock
import it.gzanee.flightfinder.ui.format.count
import it.gzanee.flightfinder.ui.format.short
import it.gzanee.flightfinder.ui.format.shortDay
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import it.gzanee.flightfinder.ui.theme.TABULAR
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Nome della città, bandiera e paese di un aeroporto, dal catalogo. */
interface PlaceNames {
    fun city(code: String): String
    fun flag(code: String): String = ""
    fun country(code: String): String = ""
    fun airport(code: String): String = ""
}

private fun Quote.airlines() = segments.map { it.airlineName.ifEmpty { it.airlineCode } }.distinct().joinToString(", ")

private fun Quote.stopsLabel() = if (stops > 0) count(stops, "scalo", "scali") else "Diretto"

/**
 * Una tratta come linea del tempo: partenza e arrivo con orario, codice e città ai lati;
 * durata e scali sopra la linea; compagnia, numero di volo e data nell'intestazione.
 */
@Composable
fun LegRow(quote: Quote, title: String, names: PlaceNames) {
    val first = quote.first
    val last = quote.last
    val numbers = quote.segments.joinToString(" + ") { "${it.airlineCode} ${it.flightNumber}" }
    val nextDay = last.arrival.toLocalDate() != first.departure.toLocalDate()
    val via = quote.segments.dropLast(1).joinToString(", ") { names.city(it.toCode) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (title == "Ritorno") Icons.Filled.FlightLand else Icons.Filled.FlightTakeoff, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    append("$title · ${quote.airlines()}")
                    withStyle(FlightFinderType.label.toSpanStyle().copy(color = colors.textSecondary)) { append("  $numbers") }
                },
                style = FlightFinderType.titleSm.copy(fontSize = FlightFinderType.body.fontSize),
                color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Tag(first.departure.shortDay())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Endpoint(first.departure.clock(), first.fromCode, names.city(first.fromCode), Alignment.Start)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${quote.durationMin.asDuration()} · ${quote.stopsLabel()}", style = FlightFinderType.label,
                    color = if (quote.stops > 0) colors.warn else colors.textSecondary, maxLines = 1,
                )
                RouteLine(Modifier.padding(vertical = 4.dp))
                if (via.isNotEmpty()) Text("via $via", style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Endpoint(last.arrival.clock() + if (nextDay) "+1" else "", last.toCode, names.city(last.toCode), Alignment.End)
        }
        if (quote.stops > 0 && quote.selfTransfer == true) HintText("Cambio volo a carico tuo: bagagli e coincidenza non sono garantiti.", warn = true)
    }
}

@Composable
private fun Endpoint(time: String, code: String, city: String, align: Alignment.Horizontal) {
    Column(Modifier.width(92.dp), horizontalAlignment = align) {
        Text(time, style = FlightFinderType.time, color = colors.textPrimary, maxLines = 1)
        Text(code, style = FlightFinderType.overline.copy(fontWeight = FontWeight.Bold), color = colors.brand)
        Text(city, style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Linea sottile con un punto a ogni capo e la freccia al centro. */
@Composable
fun RouteLine(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(colors.brand))
        Box(Modifier.weight(1f).height(1.5.dp).background(colors.border))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.brand, modifier = Modifier.padding(horizontal = 2.dp).size(14.dp))
        Box(Modifier.weight(1f).height(1.5.dp).background(colors.border))
        Box(Modifier.size(7.dp).clip(CircleShape).background(colors.brand))
    }
}

/** Separatore tratteggiato con una pillola al centro (es. "48h 40m a Danzica"). */
@Composable
fun StayDivider(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        val dash = colors.border
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(dash, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Text(
            text, style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary,
            modifier = Modifier.clip(CircleShape).background(colors.surfaceVariant).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

/** "8–10 gen", "30 dic – 2 gen", "ven 8 gen". */
private fun span(from: LocalDate, to: LocalDate?): String = when {
    to == null || to == from -> from.short()
    from.month == to.month -> "${from.dayOfMonth}–${to.format(DAY_MONTH)}"
    else -> "${from.format(DAY_MONTH)} – ${to.format(DAY_MONTH)}"
}.replace(".", "")

/** Tratta in una riga: "Andata  ven 15:05 → 16:36 · Ryanair · Diretto". */
@Composable
private fun CompactLeg(title: String, quote: Quote) {
    val nextDay = quote.last.arrival.toLocalDate() != quote.first.departure.toLocalDate()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = FlightFinderType.label.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary, modifier = Modifier.width(58.dp))
        Text(
            "${quote.first.departure.format(WEEKDAY).replace(".", "")} ${quote.first.departure.clock()} → ${quote.last.arrival.clock()}${if (nextDay) "+1" else ""}",
            style = FlightFinderType.body.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR), color = colors.textPrimary, maxLines = 1,
        )
        Text(
            "${quote.airlines()} · ${quote.stopsLabel()}", style = FlightFinderType.label,
            color = if (quote.stops > 0) colors.warn else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

/**
 * Un viaggio nella lista: meta e prezzo in alto, il tempo sul posto in una fascia tenue,
 * poi le tratte in forma compatta. `best` colora il prezzo d'arancio (il più economico).
 */
@Composable
fun TripCard(trip: Trip, names: PlaceNames, highlighted: Boolean = false, best: Boolean = false, onClick: () -> Unit) {
    val inbound = trip.inbound
    val away = inbound?.first?.fromCode
    val home = inbound?.last?.toCode
    FlightFinderCard(onClick = onClick, highlighted = highlighted) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${names.flag(trip.destination)} ${names.city(trip.destination)}".trim(), style = FlightFinderType.title,
                    color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(names.country(trip.destination), "da ${names.city(trip.origin)}" + if (home != null && home != trip.origin) ", rientro a ${names.city(home)}" else "")
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(trip.price.asEuro(), style = FlightFinderType.price, color = if (best) colors.accent else colors.textPrimary)
                Text(
                    when {
                        trip.needsRecheck == true -> "da aggiornare"
                        trip.outbound.travelers > 1 -> "totale, ${trip.outbound.travelers} persone"
                        inbound != null -> "andata e ritorno"
                        else -> "solo andata"
                    },
                    style = FlightFinderType.label, color = if (trip.needsRecheck == true) colors.warn else colors.textSecondary,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (inbound != null) Icons.Outlined.HourglassBottom else Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.brand, modifier = Modifier.size(16.dp))
            Text(
                when {
                    inbound == null -> "Solo andata"
                    trip.usefulMinutes != null -> "${trip.usefulMinutes.asDuration()} ${if (trip.destination == away) "sul posto" else "tra i voli"}"
                    else -> count(trip.nights ?: 0, "notte", "notti")
                },
                style = FlightFinderType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text(
                span(trip.outbound.first.departure.toLocalDate(), inbound?.first?.departure?.toLocalDate()) + if (inbound != null) " · ${count(trip.nights ?: 0, "notte", "notti")}" else "",
                style = FlightFinderType.label.copy(fontFeatureSettings = TABULAR), color = colors.textSecondary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactLeg("Andata", trip.outbound)
            if (inbound != null) CompactLeg("Ritorno", inbound)
        }
        val tags = buildList {
            if (best) add(Triple("Miglior prezzo", Icons.Outlined.Sell, true))
            when (trip.outbound.baggage) {
                Baggage.CABIN -> add(Triple("Trolley incluso", Icons.Outlined.Luggage, false))
                Baggage.CHECKED -> add(Triple("Valigia inclusa", Icons.Outlined.Luggage, false))
                Baggage.BASE -> Unit
            }
            if (inbound != null) add(Triple("2 biglietti separati", null, false))
            trip.roundTripFrom?.let { add(Triple("A/R unico da ${it.asEuro()}", null, false)) }
        }
        if (tags.isNotEmpty()) ChipGroup { tags.forEach { (text, icon, accent) -> Tag(text, icon, accent) } }
    }
}
