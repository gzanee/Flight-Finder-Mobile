package it.gzanee.flightfinder.ui.screens.components

import java.time.format.TextStyle
import java.time.DayOfWeek
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.format.appLocale
import androidx.compose.ui.res.stringResource
import it.gzanee.flightfinder.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.gzanee.flightfinder.domain.model.CalendarPrice
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import it.gzanee.flightfinder.ui.theme.FlightFinderType
import it.gzanee.flightfinder.ui.theme.TABULAR
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt


/**
 * Prezzo minimo per giorno di partenza, mese per mese. Il giorno più economico è pieno,
 * il terzo più economico è tinto di blu; gli altri restano neutri.
 */
@Composable
fun PriceCalendar(items: List<CalendarPrice>) {
    val byDay = items.groupBy { it.date }.mapValues { (_, list) -> list.minOf { it.price } }
    val lo = byDay.values.min()
    val hi = byDay.values.max()
    val first = YearMonth.from(byDay.keys.min())
    val last = YearMonth.from(byDay.keys.max())
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.forEach { month ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", appLocale())).replaceFirstChar { it.uppercase() }, style = FlightFinderType.title, color = colors.textPrimary)
                Row(Modifier.padding(top = 8.dp)) {
                    DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, appLocale()) }.forEach {
                        Text(it, style = FlightFinderType.overline, color = colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                val cells = List(month.atDay(1).dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map(month::atDay)
                cells.chunked(7).forEach { week ->
                    Row {
                        for (i in 0 until 7) {
                            val day = week.getOrNull(i)
                            Box(Modifier.weight(1f).padding(1.5.dp)) {
                                if (day != null) {
                                    val price = byDay[day]
                                    val cheapest = price != null && price == lo
                                    val low = price != null && !cheapest && (hi == lo || price <= lo + (hi - lo) / 3)
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                            .background(if (cheapest) colors.brandFill else if (low) colors.surfaceVariant else Color.Transparent)
                                            .padding(vertical = 7.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        val ink = when {
                                            cheapest -> colors.onBrandFill
                                            price == null -> colors.textTertiary.copy(alpha = 0.6f)
                                            else -> colors.textPrimary
                                        }
                                        Text("${day.dayOfMonth}", style = FlightFinderType.body.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR), color = ink)
                                        Text(
                                            price?.let { it.roundToInt().toDouble().asEuro().replace(" ", "") } ?: " ",
                                            style = FlightFinderType.label.copy(fontFeatureSettings = TABULAR, fontWeight = if (cheapest || low) FontWeight.SemiBold else FontWeight.Normal),
                                            color = if (cheapest) colors.onBrandFill else if (low) colors.textPrimary else colors.textTertiary, maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(colors.brandFill)
            Text(stringResource(R.string.calendar_cheapest), style = FlightFinderType.label, color = colors.textTertiary, modifier = Modifier.padding(start = 6.dp, end = 16.dp))
            LegendDot(colors.surfaceStrong)
            Text(stringResource(R.string.calendar_cheap), style = FlightFinderType.label, color = colors.textTertiary, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))
