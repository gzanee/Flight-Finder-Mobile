package it.gzanee.flightfinder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.R

/** Titoli e testo: Poppins. */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Etichette piccole, badge e cifre: Geist. */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Cifre a larghezza fissa: orari, durate e prezzi restano in colonna. */
const val TABULAR = "tnum"

/** Pochi stili: titoli grandi e decisi, tutto il resto piccolo e tranquillo. */
object FlightFinderType {
    val headlineXl = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em)
    val headline = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em)
    val title = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.01).em)
    val titleSm = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)
    val price = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR)
    val priceXl = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
    val time = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR)
    val body = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp)
    val bodySm = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)

    /** Etichetta maiuscola, piccola e spaziata (es. "VALUTA", "IN ARRIVO"). */
    val overline = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.09.em)
}

val Typography = Typography(
    displaySmall = FlightFinderType.headlineXl,
    headlineSmall = FlightFinderType.headline,
    titleLarge = FlightFinderType.headline,
    titleMedium = FlightFinderType.title,
    titleSmall = FlightFinderType.titleSm,
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = FlightFinderType.body,
    bodySmall = FlightFinderType.bodySm,
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = FlightFinderType.label,
    labelSmall = FlightFinderType.overline,
)
