package fr.pilmil.core.model.emergency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Moteur pur de cadence de réanimation cardio-pulmonaire (RCP).
 * Totalement indépendant de toute plateforme (compatible Android, iOS, JVM).
 * Recommandations ERC / PSC1 : 100 à 120 bpm (standard : 110 bpm).
 */
class CprCadenceEngine(
    private val scope: CoroutineScope,
    private val onTick: (compressionIndex: Int) -> Unit
) {
    private var job: Job? = null
    var isRunning: Boolean = false
        private set

    /**
     * Calcule l'intervalle en ms pour un tempo donné bridé entre 80 et 140 bpm.
     */
    fun computeIntervalMs(bpm: Int): Long = 60_000L / bpm.coerceIn(80, 140)

    /**
     * Démarre la cadence.
     * @param bpm Battements par minute (défaut : 110 bpm)
     * @param cycleLength Nombre de compressions avant cycle (ex: 30 pour 30:2)
     */
    fun start(bpm: Int = 110, cycleLength: Int = 30) {
        if (isRunning) return
        val interval = computeIntervalMs(bpm)
        isRunning = true

        job = scope.launch {
            var counter = 0
            while (isActive) {
                counter = (counter % cycleLength) + 1
                onTick(counter)
                delay(interval)
            }
        }
    }

    /**
     * Arrête le métronome.
     */
    fun stop() {
        job?.cancel()
        job = null
        isRunning = false
    }
}
