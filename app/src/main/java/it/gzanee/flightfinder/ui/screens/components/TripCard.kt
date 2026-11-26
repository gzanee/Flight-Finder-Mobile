package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Gli orari di una tratta da confrontare: partenza e arrivo grandi ai lati, sotto il codice
 * dell'aeroporto e il giorno; al centro durata, scali e compagnia.
 */
@Composable
private fun LegTimes(quote: Quote, names: PlaceNames?, detailed: Boolean) {
    val first = quote.first
    val last = quote.last
    val nextDay = last.arrival.toLocalDate() != first.departure.toLocalDate()
    val via = quote.segments.dropLast(1).joinToString(", ") { names?.city(it.toCode) ?: it.toCode }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(first.departure.clock(), style = FlightFinderType.time, color = colors.textPrimary)
            Text(
                if (detailed && names != null) "${first.fromCode} · ${names.city(first.fromCode)}" else "${first.fromCode} · ${first.departure.shortDay()}",
                style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.weight(1f).padding(top = 4.dp, start = 6.dp, end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(quote.durationMin.asDuration(), style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1)
            Text(
                quote.stopsLabel() + if (detailed && via.isNotEmpty()) " · $via" else "", style = FlightFinderType.label,
                color = if (quote.stops > 0) colors.warn else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            if (!detailed) Text(quote.airlines(), style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Top) {
                Text(last.arrival.clock(), style = FlightFinderType.time, color = colors.textPrimary)
                if (nextDay) Text("+1", style = FlightFinderType.label, color = colors.warn, modifier = Modifier.padding(start = 2.dp))
            }
            Text(
                if (detailed && names != null) "${names.city(last.toCode)} · ${last.toCode}" else "${last.toCode} · ${last.arrival.shortDay()}",
                style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            )
        }
    }
}

/** Una tratta nel dettaglio: etichetta con il giorno, compagnia e voli, poi gli orari da confrontare. */
@Composable
fun LegRow(quote: Quote, title: String, names: PlaceNames) {
    val numbers = quote.segments.joinToString(" + ") { "${it.airlineCode} ${it.flightNumber}" }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (title == "Ritorno") Icons.Filled.FlightLand else Icons.Filled.FlightTakeoff, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(15.dp))
            FieldLabel("$title · ${quote.first.departure.shortDay()}", Modifier.weight(1f))
            Text("${quote.airlines()} · $numbers", style = FlightFinderType.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LegTimes(quote, names, detailed = true)
        if (quote.stops > 0 && quote.selfTransfer == true) HintText("Cambio volo a carico tuo: bagagli e coincidenza non sono garantiti.", warn = true)
    }
}

/** Il tempo tra andata e ritorno, centrato fra due fili (come "5 min · 1.9 km" in TREK). */
@Composable
fun StayDivider(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Hairline(Modifier.weight(1f))
        Icon(Icons.Outlined.NightsStay, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
        Text(text, style = FlightFinderType.label, color = colors.textTertiary)
        Hairline(Modifier.weight(1f))
    }
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

/** "8 → 10 gen", "30 dic → 2 gen", "ven 8 gen". */
fun dateSpan(from: LocalDate, to: LocalDate?): String = when {
    to == null || to == from -> from.short()
    from.month == to.month -> "${from.dayOfMonth} → ${to.format(DAY_MONTH)}"
    else -> "${from.format(DAY_MONTH)} → ${to.format(DAY_MONTH)}"
}.replace(".", "")

/** Il prezzo in una pillola piena, come l'azione principale della card. */
@Composable
fun PricePill(price: Double, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(colors.brandFill).padding(horizontal = 18.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
        Text(price.asEuro(), style = FlightFinderType.price.copy(fontSize = FlightFinderType.title.fontSize), color = colors.onBrandFill)
    }
}

/**
 * Un volo nella lista dei risultati: rotta in alto, orari grandi da confrontare (andata e,
 * sotto un filo, ritorno), in fondo tempo sul posto, viaggiatori e bagaglio a sinistra e il
 * prezzo nella pillola piena a destra. `best` aggiunge il puntino verde del prezzo migliore.
 */
@Composable
fun TripCard(trip: Trip, names: PlaceNames, highlighted: Boolean = false, best: Boolean = false, onClick: () -> Unit) {
    val inbound = trip.inbound
    val away = inbound?.first?.fromCode
    val home = inbound?.last?.toCode
    FlightFinderCard(onClick = onClick, highlighted = highlighted, contentPadding = PaddingValues(20.dp), spacing = 18) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${names.city(trip.origin)} → ${names.city(trip.destination)}", style = FlightFinderType.titleSm,
                    color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text(names.flag(trip.destination), style = FlightFinderType.body)
            }
            if (best) Tag("Miglior prezzo", accent = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LegTimes(trip.outbound, names, detailed = false)
            if (inbound != null) {
                Hairline()
                LegTimes(inbound, names, detailed = false)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val stay = listOfNotNull(
                    trip.usefulMinutes?.takeIf { inbound != null }?.let { "${it.asDuration()} ${if (trip.destination == away) "sul posto" else "tra i voli"}" },
                    inbound?.let { count(trip.nights ?: 0, "notte", "notti") },
                    home?.takeIf { it != trip.origin }?.let { "rientro a ${names.city(it)}" },
                ).joinToString(" · ")
                if (stay.isNotEmpty()) Text(stay, style = FlightFinderType.label, color = colors.textPrimary, maxLines = 2)
                Text(
                    listOfNotNull(
                        when (trip.outbound.baggage) {
                            Baggage.CABIN -> "Trolley incluso"
                            Baggage.CHECKED -> "Valigia inclusa"
                            Baggage.BASE -> "Solo bagaglio piccolo"
                        },
                        if (trip.outbound.travelers > 1) "${trip.outbound.travelers} persone" else null,
                        trip.roundTripFrom?.let { "A/R unico da ${it.asEuro()}" },
                    ).joinToString(" · "),
                    style = FlightFinderType.label, color = colors.textTertiary, maxLines = 2,
                )
                if (trip.needsRecheck == true) Text("Prezzo da aggiornare", style = FlightFinderType.label, color = colors.warn)
            }
            PricePill(trip.price)
        }
    }
}

/** Riga di elenco come in TREK: cerchio a sinistra, titolo e dettaglio, qualcosa a destra. */
@Composable
fun ListRow(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    subdetail: String? = null,
    leading: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(InnerShape).clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = FlightFinderType.titleSm, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subdetail != null) Text(subdetail, style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
