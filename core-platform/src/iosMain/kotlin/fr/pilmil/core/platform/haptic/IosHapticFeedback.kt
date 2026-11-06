package fr.pilmil.core.platform.haptic

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * Implémentation iOS native du retour haptique via UIKit Taptic Engine.
 */
class IosHapticFeedback : HapticFeedback {

    private val impactGenerator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val notificationGenerator = UINotificationFeedbackGenerator()

    override fun pulseClick() {
        impactGenerator.prepare()
        impactGenerator.impactOccurred()
    }

    override fun pulseWarning() {
        notificationGenerator.prepare()
        notificationGenerator.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
    }

    override fun isSupported(): Boolean = true
}
