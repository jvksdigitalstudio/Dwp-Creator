package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.curve.LfoWaveforms
import com.jvk.dwpcreator.sampler.api.rhythm.LfoSync
import com.jvk.dwpcreator.sampler.api.state.LfoRateMode
import com.jvk.dwpcreator.sampler.api.state.LfoShape
import com.jvk.dwpcreator.sampler.api.state.LfoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LfoTest {
    private val sr = 48_000f

    private fun settings(state: LfoState, bpm: Float = 120f) = LfoSettings.from(state, bpm, sr)

    /** Avanza [seconds] en pasos de control de 16 muestras y devuelve la última salida. */
    private fun run(osc: LfoOscillator, s: LfoSettings, seconds: Float): Float {
        var out = 0f
        repeat((seconds * sr / 16f).toInt()) { out = osc.advance(16, s) }
        return out
    }

    @Test
    fun waveformSpotValues() {
        assertEquals(0f, LfoWaveforms.value(LfoShape.SINE, 0f), 0.002f)
        assertEquals(1f, LfoWaveforms.value(LfoShape.SINE, 0.25f), 0.002f)
        assertEquals(-1f, LfoWaveforms.value(LfoShape.SINE, 0.75f), 0.002f)
        assertEquals(1f, LfoWaveforms.value(LfoShape.TRIANGLE, 0.25f), 1e-6f)
        assertEquals(-1f, LfoWaveforms.value(LfoShape.TRIANGLE, 0.75f), 1e-6f)
        assertEquals(1f, LfoWaveforms.value(LfoShape.SAW, 0f), 1e-6f)
        assertEquals(0f, LfoWaveforms.value(LfoShape.SAW, 0.5f), 1e-6f)
        assertEquals(1f, LfoWaveforms.value(LfoShape.SQUARE, 0.25f), 0f)
        assertEquals(-1f, LfoWaveforms.value(LfoShape.SQUARE, 0.75f), 0f)
        assertEquals(0.4f, LfoWaveforms.value(LfoShape.SAMPLE_HOLD, 0.9f, held = 0.4f, next = -0.8f), 0f)
        assertEquals(0.4f, LfoWaveforms.value(LfoShape.SMOOTH_RANDOM, 0f, held = 0.4f, next = -0.8f), 1e-6f)
        assertEquals(-0.8f, LfoWaveforms.value(LfoShape.SMOOTH_RANDOM, 1f, held = 0.4f, next = -0.8f), 1e-6f)
    }

    @Test
    fun sineFollowsTheConfiguredFrequency() {
        val s = settings(LfoState(rateHz = 1f, amount = 1f))
        val osc = LfoOscillator()
        osc.restart(s, 1)
        assertEquals(1f, run(osc, s, 0.25f), 0.02f)
        assertEquals(0f, run(osc, s, 0.25f), 0.02f)
        assertEquals(-1f, run(osc, s, 0.25f), 0.02f)
    }

    @Test
    fun startPhaseSetsWhereTheCycleBegins() {
        val s = settings(LfoState(rateHz = 1f, amount = 1f, phase = 0.25f))
        val osc = LfoOscillator()
        osc.restart(s, 1)
        assertEquals(1f, osc.advance(1, s), 0.01f)
    }

    @Test
    fun tempoModeDerivesTheRateFromBpmAndDivision() {
        val quarter = LfoSync.NAMES.indexOf("1/4")
        val bar = LfoSync.NAMES.indexOf("1 bar")
        assertEquals(2f, LfoSync.rateHz(120f, quarter), 1e-4f)
        assertEquals(0.5f, LfoSync.rateHz(120f, bar), 1e-4f)
        val s = settings(LfoState(rateMode = LfoRateMode.TEMPO, division = quarter, rateHz = 9f, amount = 1f), bpm = 90f)
        assertEquals(1.5f, s.rateHz, 1e-4f)
    }

    @Test
    fun oneShotRunsOneCycleAndHoldsTheFinalValue() {
        val s = settings(LfoState(rateMode = LfoRateMode.ONE_SHOT, rateHz = 10f, shape = LfoShape.SAW, amount = 1f))
        val osc = LfoOscillator()
        osc.restart(s, 1)
        run(osc, s, 0.5f)
        assertTrue(osc.output < -0.99f)
        assertEquals(osc.output, run(osc, s, 0.5f), 0f)
    }

    @Test
    fun fadeInRampsTheDepthLinearly() {
        val s = settings(LfoState(rateHz = 1f, shape = LfoShape.SQUARE, amount = 1f, fadeMs = 100f))
        val osc = LfoOscillator()
        osc.restart(s, 1)
        assertEquals(0.5f, run(osc, s, 0.05f), 0.02f)
        assertEquals(1f, run(osc, s, 0.1f), 0f)
    }

    @Test
    fun sampleAndHoldHoldsWithinACycleAndChangesAcrossCycles() {
        val s = settings(LfoState(rateHz = 2f, shape = LfoShape.SAMPLE_HOLD, amount = 1f))
        val osc = LfoOscillator()
        osc.restart(s, 7)
        val first = osc.advance(16, s)
        assertEquals(first, run(osc, s, 0.4f), 0f)           // sigue dentro del primer ciclo (0,5 s)
        val values = HashSet<Float>()
        repeat(8) { values += run(osc, s, 0.5f) }
        assertTrue("debe cambiar de valor entre ciclos: $values", values.size > 3)
    }

    @Test
    fun randomShapesAreDeterministicPerSeedAndDifferAcrossSeeds() {
        val s = settings(LfoState(rateHz = 3f, shape = LfoShape.SMOOTH_RANDOM, amount = 1f))
        val a = LfoOscillator().also { it.restart(s, 1) }
        val b = LfoOscillator().also { it.restart(s, 1) }
        val c = LfoOscillator().also { it.restart(s, 2) }
        repeat(500) {
            assertEquals(a.advance(16, s), b.advance(16, s), 0f)
            c.advance(16, s)
        }
        assertNotEquals(a.output, c.output)
        assertTrue(abs(a.output) <= 1f)
    }

    @Test
    fun anInactiveLfoIsFlaggedSoItCanBeSkipped() {
        assertTrue(!settings(LfoState(amount = 0f)).active)
        assertTrue(settings(LfoState(amount = -0.3f)).active)
    }

    @Test
    fun syncDivisionsAreOrderedAndNamed() {
        assertEquals(12, LfoSync.NAMES.size)
        assertEquals(LfoSync.NAMES.size - 1, LfoSync.LAST_INDEX)
        assertEquals("1/4", LfoSync.NAMES[LfoSync.DEFAULT_INDEX])
        assertEquals(LfoSync.LAST_INDEX, LfoSync.indexOf(99f))
        assertEquals(0, LfoSync.indexOf(-5f))
    }
}
