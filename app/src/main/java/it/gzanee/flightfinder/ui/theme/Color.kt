package it.gzanee.flightfinder.ui.theme

import androidx.compose.ui.graphics.Color

// Palette quasi monocroma: tela calda, inchiostro quasi nero, l'elemento attivo
// pieno (nero di giorno, chiaro di notte). Il colore compare solo negli stati.

// ─── Light theme ───
val CanvasTopLight = Color(0xFFFBFAF7)
val CanvasLight = Color(0xFFF3F2EF)
val CanvasBottomLight = Color(0xFFE9E7E1)
val CardLight = Color(0xFFF9F8F6)        // bianco 55% sulla tela
val NestedLight = Color(0xFFFDFDFC)      // superficie interna: bianco 68% sulla card, più chiara
val NestedEdgeLight = Color(0xE6FFFFFF)  // bordo bianco 90%
val InnerLight = Color(0xFFF0EFED)       // inchiostro 4%: chip, binari, cerchi delle icone
val InnerStrongLight = Color(0xFFE6E5E2)
val GlassLight = Color(0xB8FFFFFF)       // barra in alto, bianco ~70%
val DockLight = Color(0xD9FFFFFF)        // dock, bianco 85%: più coprente
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
val CardDark = Color(0xFF131317)
val NestedDark = Color(0xFF1F1F23)       // bianco 7% sulla card
val NestedEdgeDark = Color(0x1AFFFFFF)   // bordo bianco 10%
val InnerDark = Color(0xFF26262A)        // bianco 9%
val InnerStrongDark = Color(0xFF313136)
val GlassDark = Color(0xB81A1A1F)
val DockDark = Color(0xD91A1A1F)
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
