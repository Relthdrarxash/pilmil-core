package fr.pilmil.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens de design transversaux de Pilmil Studio.
 * Conçus pour être sobres, ergonomiques et reposants pour les yeux (OLED Black, contrastes AAA/AA).
 */
object PilmilColors {
    // Fond noir OLED absolu (Sommelle sas calme & Sauve urgence de nuit)
    val PureBlack = Color(0xFF000000)
    val DarkSurface = Color(0xFF121212)
    val DarkContainer = Color(0xFF1E1E1E)

    // Neutres papier (Lunaison & Sillon mode clair)
    val PaperLight = Color(0xFFFFFCFA)
    val CreamSurface = Color(0xFFFBF5F2)
    val TextInk = Color(0xFF1C1B1F)
    val TextMuted = Color(0xFF49454F)

    // Accents fonctionnels
    val LifeGreen = Color(0xFF2E7D32)     // Sillon / Réussite
    val AlertCrimson = Color(0xFFB3261E)  // Sauve / Urgence
    val PlumPrimary = Color(0xFF4A2C4F)   // Lunaison
    val NightBlue = Color(0xFF1A237E)     // Sommelle
}

object PilmilSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
