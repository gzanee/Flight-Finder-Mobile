package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
 * Una tratta: cerchio con l'aereo, etichetta maiuscola con il giorno, gli orari grandi,
 * poi due righe grigie con le città e con compagnia, volo, durata e scali.
 */
@Composable
fun LegRow(quote: Quote, title: String, names: PlaceNames) {
    val first = quote.first
    val last = quote.last
    val numbers = quote.segments.joinToString(" + ") { "${it.airlineCode} ${it.flightNumber}" }
    val nextDay = last.arrival.toLocalDate() != first.departure.toLocalDate()
    val via = quote.segments.dropLast(1).joinToString(", ") { names.city(it.toCode) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(if (title == "Ritorno") Icons.Filled.FlightLand else Icons.Filled.FlightTakeoff)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FieldLabel("$title · ${first.departure.shortDay()}")
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${first.departure.clock()} → ${last.arrival.clock()}", style = FlightFinderType.time, color = colors.textPrimary)
                if (nextDay) Text(" +1", style = FlightFinderType.label, color = colors.warn, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(
                "${names.city(first.fromCode)} ${first.fromCode} → ${names.city(last.toCode)} ${last.toCode}",
                style = FlightFinderType.bodySm, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${quote.airlines()} · $numbers · ${quote.durationMin.asDuration()} · ${quote.stopsLabel()}" + if (via.isNotEmpty()) " via $via" else "",
                style = FlightFinderType.label, color = if (quote.stops > 0) colors.warn else colors.textTertiary,
            )
            if (quote.stops > 0 && quote.selfTransfer == true) HintText("Cambio volo a carico tuo: bagagli e coincidenza non sono garantiti.", warn = true)
        }
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

private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

/** "8 → 10 gen", "30 dic → 2 gen", "ven 8 gen". */
fun dateSpan(from: LocalDate, to: LocalDate?): String = when {
    to == null || to == from -> from.short()
    from.month == to.month -> "${from.dayOfMonth} → ${to.format(DAY_MONTH)}"
    else -> "${from.format(DAY_MONTH)} → ${to.format(DAY_MONTH)}"
}.replace(".", "")

/** Tratta in una riga: "ANDATA  ven 15:05 → 16:36    Ryanair · Diretto". */
@Composable
private fun CompactLeg(title: String, quote: Quote) {
    val nextDay = quote.last.arrival.toLocalDate() != quote.first.departure.toLocalDate()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(title, Modifier.width(70.dp))
        Text(
            "${quote.first.departure.format(WEEKDAY).replace(".", "")} ${quote.first.departure.clock()} → ${quote.last.arrival.clock()}${if (nextDay) "+1" else ""}",
            style = FlightFinderType.body.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR), color = colors.textPrimary, maxLines = 1,
        )
        Text(
            "${quote.airlines()} · ${quote.stopsLabel()}", style = FlightFinderType.label,
            color = if (quote.stops > 0) colors.warn else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

/**
 * Un viaggio nella lista: meta e prezzo, poche pillole (date, tempo sul posto, bagaglio)
 * e le tratte in un riquadro tenue. `best` mette il puntino verde del prezzo migliore.
 */
@Composable
fun TripCard(trip: Trip, names: PlaceNames, highlighted: Boolean = false, best: Boolean = false, onClick: () -> Unit) {
    val inbound = trip.inbound
    val away = inbound?.first?.fromCode
    val home = inbound?.last?.toCode
    FlightFinderCard(onClick = onClick, highlighted = highlighted, spacing = 16) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${names.city(trip.destination)} ${names.flag(trip.destination)}".trim(), style = FlightFinderType.title,
                    color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(names.country(trip.destination), "da ${names.city(trip.origin)}" + if (home != null && home != trip.origin) ", rientro a ${names.city(home)}" else "")
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    style = FlightFinderType.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(trip.price.asEuro(), style = FlightFinderType.price, color = colors.textPrimary)
                Text(
                    when {
                        trip.needsRecheck == true -> "da aggiornare"
                        trip.outbound.travelers > 1 -> "${trip.outbound.travelers} persone"
                        inbound != null -> "andata e ritorno"
                        else -> "solo andata"
                    },
                    style = FlightFinderType.label, color = if (trip.needsRecheck == true) colors.warn else colors.textTertiary,
                )
            }
        }
        ChipGroup {
            if (best) Tag("Miglior prezzo", accent = true)
            Tag(dateSpan(trip.outbound.first.departure.toLocalDate(), inbound?.first?.departure?.toLocalDate()))
            if (inbound != null) {
                Tag(trip.usefulMinutes?.let { "${it.asDuration()} ${if (trip.destination == away) "sul posto" else "tra i voli"}" } ?: count(trip.nights ?: 0, "notte", "notti"))
            }
            when (trip.outbound.baggage) {
                Baggage.CABIN -> Tag("Trolley", Icons.Outlined.Luggage)
                Baggage.CHECKED -> Tag("Valigia", Icons.Outlined.Luggage)
                Baggage.BASE -> Unit
            }
            trip.roundTripFrom?.let { Tag("A/R unico da ${it.asEuro()}") }
        }
        Column(
            Modifier.fillMaxWidth().clip(InnerShape).background(colors.surfaceVariant).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactLeg("Andata", trip.outbound)
            if (inbound != null) CompactLeg("Ritorno", inbound)
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
