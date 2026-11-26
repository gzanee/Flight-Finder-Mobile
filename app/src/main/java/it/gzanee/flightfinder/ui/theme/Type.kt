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
    // Poppins: titoli 600–700, corpo 400–500. Geist sempre a 500 per etichette, metadati e cifre minori.
    val headlineXl = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-0.02).em)
    val headline = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em)
    val title = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.01).em)
    val titleSm = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
    val price = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR)
    val priceXl = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
    /** Orari di partenza e arrivo: l'informazione da confrontare, quindi grandi. */
    val time = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR)
    val body = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val bodySm = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp)

    /** Etichetta maiuscola breve, piccola e spaziata (es. "ANDATA", "PERIODO"). */
    val overline = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.09.em)
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
