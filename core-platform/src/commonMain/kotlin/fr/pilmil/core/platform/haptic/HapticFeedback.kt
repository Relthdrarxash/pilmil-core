package fr.pilmil.core.platform.haptic

/**
 * Interface d'abstraction haptique multiplateforme (Android & iOS).
 * Permet d'émettre des vibrations de cadence et des retours tactiles
 * sans coupler le domaine métier aux SDKs natifs (Vibrator vs UIImpactFeedbackGenerator).
 */
interface HapticFeedback {
    /** Déclenche une pulsation brève et nette (battement de métronome). */
    fun pulseClick()

    /** Déclenche une vibration d'alerte ou de transition d'état. */
    fun pulseWarning()

    /** Indique si le matériel supporte le retour haptique. */
    fun isSupported(): Boolean
}
