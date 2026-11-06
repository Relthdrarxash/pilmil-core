package fr.pilmil.core.model.emergency

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CprCadenceEngineTest {

    @Test
    fun intervalCalculationsAreAccurateAndClamped() = runTest {
        val engine = CprCadenceEngine(this) {}

        // Standard ERC 110 bpm -> 545 ms
        assertEquals(545L, engine.computeIntervalMs(110))
        // 100 bpm -> 600 ms
        assertEquals(600L, engine.computeIntervalMs(100))
        // 120 bpm -> 500 ms
        assertEquals(500L, engine.computeIntervalMs(120))

        // Clamping bounds
        assertEquals(750L, engine.computeIntervalMs(40)) // Clamped to 80
        assertEquals(428L, engine.computeIntervalMs(250)) // Clamped to 140
    }

    @Test
    fun cadenceTicksWithAccurateCycleCount() = runTest {
        val ticks = mutableListOf<Int>()
        val engine = CprCadenceEngine(this) { count ->
            ticks.add(count)
        }

        assertFalse(engine.isRunning)
        engine.start(bpm = 100, cycleLength = 3) // 600 ms per tick
        assertTrue(engine.isRunning)

        // Yield scheduler to trigger first tick
        testScheduler.runCurrent()
        assertEquals(listOf(1), ticks)

        // Advance 600 ms -> tick 2
        testScheduler.advanceTimeBy(600)
        testScheduler.runCurrent()
        assertEquals(listOf(1, 2), ticks)

        // Advance 600 ms -> tick 3
        testScheduler.advanceTimeBy(600)
        testScheduler.runCurrent()
        assertEquals(listOf(1, 2, 3), ticks)

        // Advance 600 ms -> tick 1 (new cycle)
        testScheduler.advanceTimeBy(600)
        testScheduler.runCurrent()
        assertEquals(listOf(1, 2, 3, 1), ticks)

        engine.stop()
        assertFalse(engine.isRunning)

        // No more ticks after stop
        testScheduler.advanceTimeBy(1200)
        testScheduler.runCurrent()
        assertEquals(listOf(1, 2, 3, 1), ticks)
    }
}
