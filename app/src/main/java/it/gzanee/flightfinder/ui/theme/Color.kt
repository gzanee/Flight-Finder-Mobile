package it.gzanee.flightfinder.ui.theme

import androidx.compose.ui.graphics.Color

// Palette quasi monocroma: tela calda, inchiostro quasi nero, l'elemento attivo
// pieno (nero di giorno, chiaro di notte). Il colore compare solo negli stati.

// ─── Light theme ───
val CanvasTopLight = Color(0xFFFBFAF7)
val CanvasLight = Color(0xFFF3F2EF)
val CanvasBottomLight = Color(0xFFE9E7E1)
val CardLight = Color(0xFFFBFAF8)
val InnerLight = Color(0xFFF1F0EC)        // riquadri dentro le card, chip spente
val InnerStrongLight = Color(0xFFE7E5E0)
val InkLight = Color(0xFF101013)
val MutedLight = Color(0xFF68686F)
val FaintLight = Color(0xFF9A9AA1)
val HairlineLight = Color(0x0D101013)     // righe e bordi appena visibili
val ActLight = Color(0xFF101013)
val OnActLight = Color(0xFFFFFFFF)

// ─── Dark theme ───
val CanvasTopDark = Color(0xFF17171C)
val CanvasDark = Color(0xFF0C0C0F)
val CanvasBottomDark = Color(0xFF08080A)
val CardDark = Color(0xFF141418)
val InnerDark = Color(0xFF1D1D22)
val InnerStrongDark = Color(0xFF26262C)
val InkDark = Color(0xFFF5F5F7)
val MutedDark = Color(0xFF9C9CA3)
val FaintDark = Color(0xFF6E6E76)
val HairlineDark = Color(0x14FFFFFF)
val ActDark = Color(0xFFF5F5F7)
val OnActDark = Color(0xFF101013)

// ─── Stati (uguali nei due temi) ───
val Confirmed = Color(0xFF2FA37A)
val Pending = Color(0xFFE8A13A)
val PendingInk = Color(0xFFB0700F)
val Danger = Color(0xFFD6273B)
val Info = Color(0xFF4A7DDB)
