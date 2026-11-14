package fr.pilmil.core.designsystem.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.pilmil.core.designsystem.PilmilColors
import fr.pilmil.core.platform.haptic.HapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Carte interactive à balayage horizontal (Swipeable Card) avec retours haptiques magnétiques.
 * Conçue pour une ergonomie à un seul pouce, sans latence et fluide à 120 Hz.
 *
 * Accessibilité : un balayage n'est pas faisable avec TalkBack, donc chaque callback fourni
 * est aussi exposé comme action personnalisée ; elle n'est créée que si son libellé est
 * fourni (le design system ne porte aucune chaîne localisée).
 *
 * @param haptic Moteur haptique multiplateforme pour les clics de crantage
 * @param onSwipeRight Action déclenchée si le balayage dépasse le seuil droit
 * @param onSwipeLeft Action déclenchée si le balayage dépasse le seuil gauche
 * @param swipeRightLabel Libellé localisé de l'action de droite pour les services d'accessibilité
 * @param swipeLeftLabel Libellé localisé de l'action de gauche pour les services d'accessibilité
 * @param containerColor Fond de la carte (arrière-plan révélé au balayage) ; passer
 * `MaterialTheme.colorScheme.*` pour suivre le thème de l'application
 * @param surfaceColor Couleur de la surface déplaçable
 * @param contentColor Couleur du contenu
 * @param content Contenu de la carte
 */
@Composable
fun PilmilSwipeableCard(
    modifier: Modifier = Modifier,
    haptic: HapticFeedback? = null,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    thresholdDp: Float = 90f,
    swipeRightLabel: String? = null,
    swipeLeftLabel: String? = null,
    containerColor: Color = PilmilColors.DarkContainer,
    surfaceColor: Color = PilmilColors.DarkSurface,
    contentColor: Color = PilmilColors.PaperLight,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { thresholdDp.dp.toPx() }
    val scope = rememberCoroutineScope()

    // Muté de façon synchrone à chaque delta de drag : une coroutine par delta serait trop lente
    var offsetPx by remember { mutableFloatStateOf(0f) }
    // Non observable : seul onDragStarted le lit, aucune recomposition n'est utile
    val returnJob = remember { arrayOfNulls<Job>(1) }

    // Mémorise si le cran haptique a déjà vibré pour ce geste
    var hasTriggeredHapticRight by remember { mutableStateOf(false) }
    var hasTriggeredHapticLeft by remember { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        val newOffset = offsetPx + delta
        offsetPx = newOffset

        // Détection du passage de seuil vers la droite
        if (newOffset >= thresholdPx && !hasTriggeredHapticRight) {
            haptic?.pulseClick()
            hasTriggeredHapticRight = true
        } else if (newOffset < thresholdPx && hasTriggeredHapticRight) {
            hasTriggeredHapticRight = false
        }

        // Détection du passage de seuil vers la gauche
        if (newOffset <= -thresholdPx && !hasTriggeredHapticLeft) {
            haptic?.pulseClick()
            hasTriggeredHapticLeft = true
        } else if (newOffset > -thresholdPx && hasTriggeredHapticLeft) {
            hasTriggeredHapticLeft = false
        }
    }

    // Le geste de balayage est inaccessible à TalkBack et au clavier : on expose les mêmes actions
    val swipeActions = buildList {
        if (onSwipeRight != null && swipeRightLabel != null) {
            add(CustomAccessibilityAction(swipeRightLabel) { onSwipeRight(); true })
        }
        if (onSwipeLeft != null && swipeLeftLabel != null) {
            add(CustomAccessibilityAction(swipeLeftLabel) { onSwipeLeft(); true })
        }
    }

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .semantics(mergeDescendants = true) { this.customActions = swipeActions }
    ) {
        // Arrière-plan révélateur selon la direction
        val currentOffset = offsetPx
        val backgroundColor = when {
            currentOffset > 30f -> PilmilColors.LifeGreen.copy(alpha = (currentOffset / thresholdPx).coerceIn(0.1f, 0.4f))
            currentOffset < -30f -> PilmilColors.TextMuted.copy(alpha = (-currentOffset / thresholdPx).coerceIn(0.1f, 0.3f))
            else -> Color.Transparent
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor)
        )

        // Carte déplaçable avec physique de ressort fluide
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        returnJob[0]?.cancel()
                        hasTriggeredHapticRight = false
                        hasTriggeredHapticLeft = false
                    },
                    onDragStopped = {
                        val finalOffset = offsetPx
                        if (finalOffset >= thresholdPx && onSwipeRight != null) {
                            haptic?.pulseWarning()
                            onSwipeRight()
                        } else if (finalOffset <= -thresholdPx && onSwipeLeft != null) {
                            haptic?.pulseWarning()
                            onSwipeLeft()
                        }
                        // Retour élastique immédiat
                        returnJob[0] = scope.launch {
                            animate(
                                initialValue = offsetPx,
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { value, _ -> offsetPx = value }
                        }
                    }
                ),
            shape = shape,
            color = surfaceColor,
            contentColor = contentColor
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
