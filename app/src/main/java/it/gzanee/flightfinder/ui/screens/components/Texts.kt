package it.gzanee.flightfinder.ui.screens.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.ui.theme.FlightFinderColorScheme.colors

@Composable
fun FieldLabel(text: String) = Text(text, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun HintText(text: String, modifier: Modifier = Modifier, warn: Boolean = false) =
    Text(text, modifier = modifier, color = if (warn) colors.warn else colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
fun SectionTitle(text: String) = Text(text, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
