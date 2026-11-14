package fr.pilmil.core.platform.haptic

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * Implémentation iOS native du retour haptique via UIKit Taptic Engine.
 *
 * À instancier sur le thread principal : UIKit l'exige pour les générateurs de feedback,
 * et `prepare()` est appelé dès l'initialisation.
 */
class IosHapticFeedback : HapticFeedback {

    private val impactGenerator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val notificationGenerator = UINotificationFeedbackGenerator()

    init {
        // Réveille le Taptic Engine dès la création pour réduire la latence du premier retour
        impactGenerator.prepare()
        notificationGenerator.prepare()
    }

    override fun pulseClick() {
        impactGenerator.impactOccurred()
        // Ré-arme le moteur pour le prochain cran du geste
        impactGenerator.prepare()
    }

    override fun pulseWarning() {
        notificationGenerator.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
        notificationGenerator.prepare()
    }

    override fun isSupported(): Boolean = true
}
