package fr.pilmil.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens de design transversaux de Pilmil Studio (Jetpack Compose).
 * Conçus pour être sobres, ergonomiques et reposants pour les yeux (OLED Black, contrastes AAA/AA).
 */
object PilmilColors {
    // Fond noir OLED absolu (Sommelle sas calme & Sauve urgence de nuit)
    val PureBlack = Color(PilmilColorValues.PureBlack)
    val DarkSurface = Color(PilmilColorValues.DarkSurface)
    val DarkContainer = Color(PilmilColorValues.DarkContainer)

    // Neutres papier (Lunaison & Sillon mode clair)
    val PaperLight = Color(PilmilColorValues.PaperLight)
    val CreamSurface = Color(PilmilColorValues.CreamSurface)
    val TextInk = Color(PilmilColorValues.TextInk)
    val TextMuted = Color(PilmilColorValues.TextMuted)

    // Accents fonctionnels
    val LifeGreen = Color(PilmilColorValues.LifeGreen)     // Sillon / Réussite
    val AlertCrimson = Color(PilmilColorValues.AlertCrimson)  // Sauve / Urgence
    val PlumPrimary = Color(PilmilColorValues.PlumPrimary)   // Lunaison
    val NightBlue = Color(PilmilColorValues.NightBlue)     // Sommelle
}

object PilmilSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
