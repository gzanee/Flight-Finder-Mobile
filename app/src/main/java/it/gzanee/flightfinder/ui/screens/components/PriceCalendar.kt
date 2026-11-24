package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import it.gzanee.flightfinder.domain.model.CalendarPrice
import it.gzanee.flightfinder.ui.format.asEuro
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

/** Prezzo minimo per giorno di partenza, mese per mese: verde il terzo più economico. */
@Composable
fun PriceCalendar(items: List<CalendarPrice>) {
    val byDay = items.groupBy { it.date }.mapValues { (_, list) -> list.minOf { it.price } }
    val lo = byDay.values.min()
    val hi = byDay.values.max()
    val first = YearMonth.from(byDay.keys.min())
    val last = YearMonth.from(byDay.keys.max())
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HintText("Prezzo minimo per giorno di partenza. Verde: più economico.")
        generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.forEach { month ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(month.format(MONTH).replaceFirstChar { it.uppercase() }, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Row { listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom").forEach { Text(it, color = colors.textTertiary, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                val cells = List(month.atDay(1).dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map(month::atDay)
                cells.chunked(7).forEach { week ->
                    Row {
                        for (i in 0 until 7) {
                            val day = week.getOrNull(i)
                            Box(Modifier.weight(1f).padding(1.5.dp)) {
                                if (day != null) {
                                    val price = byDay[day]
                                    val tier = when {
                                        price == null -> colors.surface
                                        hi == lo || price <= lo + (hi - lo) / 3 -> colors.goodSoft
                                        price <= lo + 2 * (hi - lo) / 3 -> colors.warnSoft
                                        else -> colors.badSoft
                                    }
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(tier)
                                            .then(if (price == lo) Modifier.border(1.5.dp, colors.good, RoundedCornerShape(6.dp)) else Modifier)
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text("${day.dayOfMonth}", color = if (price != null) colors.textPrimary else colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        if (price != null) Text(price.roundToInt().toDouble().asEuro(), color = colors.textPrimary, fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
