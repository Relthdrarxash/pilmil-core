package fr.pilmil.core.designsystem.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.pilmil.core.designsystem.PilmilColors
import fr.pilmil.core.platform.haptic.HapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Carte interactive à balayage horizontal (Swipeable Card) avec retours haptiques magnétiques.
 * Conçue pour une ergonomie à un seul pouce, sans latence et fluide à 120 Hz.
 *
 * @param haptic Moteur haptique multiplateforme pour les clics de crantage
 * @param onSwipeRight Action déclenchée si le balayage dépasse le seuil droit
 * @param onSwipeLeft Action déclenchée si le balayage dépasse le seuil gauche
 * @param content Contenu de la carte
 */
@Composable
fun PilmilSwipeableCard(
    modifier: Modifier = Modifier,
    haptic: HapticFeedback? = null,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    thresholdDp: Float = 90f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { thresholdDp.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Mémorise si le cran haptique a déjà vibré pour ce geste
    var hasTriggeredHapticRight by remember { mutableStateOf(false) }
    var hasTriggeredHapticLeft by remember { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            val newOffset = offsetX.value + delta
            offsetX.snapTo(newOffset)

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
    }

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PilmilColors.DarkContainer)
    ) {
        // Arrière-plan révélateur selon la direction
        val currentOffset = offsetX.value
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
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val finalOffset = offsetX.value
                        if (finalOffset >= thresholdPx && onSwipeRight != null) {
                            haptic?.pulseWarning()
                            onSwipeRight()
                        } else if (finalOffset <= -thresholdPx && onSwipeLeft != null) {
                            haptic?.pulseWarning()
                            onSwipeLeft()
                        }
                        // Retour élastique immédiat
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            hasTriggeredHapticRight = false
                            hasTriggeredHapticLeft = false
                        }
                    }
                ),
            shape = shape,
            color = PilmilColors.DarkSurface,
            contentColor = PilmilColors.PaperLight
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
