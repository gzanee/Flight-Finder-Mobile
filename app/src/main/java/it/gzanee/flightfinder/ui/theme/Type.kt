package it.gzanee.flightfinder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.R

/** Titoli: Plus Jakarta Sans, con spaziatura stretta. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

/** Testo e dati: Inter. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Cifre a larghezza fissa: orari, durate e prezzi restano in colonna. */
const val TABULAR = "tnum"

/** Stili ricorrenti oltre a quelli di Material. */
object FlightFinderType {
    val headlineXl = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.015).em)
    val headline = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em)
    val title = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.005).em)
    val titleSm = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val price = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR)
    val priceXl = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
    val time = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val bodySm = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em)

    /** Etichetta maiuscola sopra un gruppo (es. "SELEZIONATI"). */
    val overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em)
}

val Typography = Typography(
    displaySmall = FlightFinderType.headlineXl,
    headlineSmall = FlightFinderType.headline,
    titleLarge = FlightFinderType.headline,
    titleMedium = FlightFinderType.title,
    titleSmall = FlightFinderType.titleSm,
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = FlightFinderType.body,
    bodySmall = FlightFinderType.bodySm,
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = FlightFinderType.label,
    labelSmall = FlightFinderType.overline,
)
