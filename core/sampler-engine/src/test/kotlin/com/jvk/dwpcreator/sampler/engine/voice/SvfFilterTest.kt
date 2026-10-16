package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.curve.FilterResponse
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

class SvfFilterTest {
    private val sr = 48_000f

    /** Ganancia en dB de un seno de [frequency] Hz a través del filtro (segunda mitad de 1 s, ya estabilizado). */
    private fun gainDb(mode: FilterMode, cutoffHz: Float, resonance: Float, frequency: Float, drive: Float = 0f): Float {
        val coefficients = SvfCoefficients().also { it.update(mode, cutoffHz, resonance, drive, sr) }
        val filter = SvfFilter()
        val frames = 48_000
        var inEnergy = 0.0
        var outEnergy = 0.0
        for (i in 0 until frames) {
            val x = (0.5 * sin(2.0 * PI * frequency * i / sr)).toFloat()
            val y = filter.process(x, coefficients)
            if (i >= frames / 2) {
                inEnergy += x.toDouble() * x
                outEnergy += y.toDouble() * y
            }
        }
        return (10.0 * log10(outEnergy / inEnergy)).toFloat()
    }

    /** Debe coincidir con la curva analítica que dibuja la UI. */
    private fun assertMatchesAnalyticResponse(mode: FilterMode, cutoffHz: Float, resonance: Float, frequency: Float) {
        assertEquals(
            "$mode fc=$cutoffHz res=$resonance f=$frequency",
            FilterResponse.magnitudeDb(mode, cutoffHz, resonance, frequency),
            gainDb(mode, cutoffHz, resonance, frequency),
            1.0f
        )
    }

    @Test
    fun lowpass12MatchesTheAnalyticCurve() {
        for (f in listOf(250f, 1000f, 4000f)) assertMatchesAnalyticResponse(FilterMode.LOWPASS_12, 1000f, 0f, f)
    }

    @Test
    fun lowpass24IsTwiceAsSteep() {
        for (f in listOf(250f, 1000f, 4000f)) assertMatchesAnalyticResponse(FilterMode.LOWPASS_24, 1000f, 0f, f)
        assertTrue(gainDb(FilterMode.LOWPASS_24, 1000f, 0f, 4000f) < gainDb(FilterMode.LOWPASS_12, 1000f, 0f, 4000f) - 15f)
    }

    @Test
    fun highpassBandpassAndNotchMatchTheAnalyticCurve() {
        for (f in listOf(250f, 1000f, 4000f)) assertMatchesAnalyticResponse(FilterMode.HIGHPASS_12, 1000f, 0f, f)
        for (f in listOf(500f, 1000f, 2000f)) assertMatchesAnalyticResponse(FilterMode.BANDPASS, 1000f, 0.3f, f)
        for (f in listOf(250f, 990f, 4000f)) assertMatchesAnalyticResponse(FilterMode.NOTCH, 1000f, 0.5f, f)
    }

    @Test
    fun resonanceCreatesAPeakAtTheCutoff() {
        assertEquals(20f, gainDb(FilterMode.LOWPASS_12, 1000f, 1f, 1000f), 1.0f) // Q = 10 -> +20 dB
        assertTrue(gainDb(FilterMode.LOWPASS_12, 1000f, 0f, 1000f) < -2.5f)
    }

    @Test
    fun bandpassHasUnityGainAtTheCutoff() {
        assertEquals(0f, gainDb(FilterMode.BANDPASS, 1000f, 0.5f, 1000f), 0.5f)
    }

    @Test
    fun extremeSettingsStayFiniteAndBoundedOnNoise() {
        val coefficients = SvfCoefficients()
        val filters = FilterMode.entries.map { SvfFilter() }
        var seed = 12345
        for ((index, mode) in FilterMode.entries.withIndex()) {
            val filter = filters[index]
            for (i in 0 until 48_000) {
                if (i % 16 == 0) {
                    // Barrido rápido de corte y resonancia al máximo: el caso más hostil para la estabilidad.
                    val cutoff = 30f + (i % 4_000) * 5f
                    coefficients.update(mode, cutoff, 1f, 0.5f, sr)
                }
                seed = seed * 1_103_515_245 + 12_345
                val x = ((seed ushr 8) % 2_000 - 1_000) / 1_000f
                val y = filter.process(x, coefficients)
                assertTrue("$mode produjo $y", y.isFinite() && abs(y) < 200f)
            }
        }
    }

    @Test
    fun driveSaturatesWithoutExceedingTheInputLevel() {
        val coefficients = SvfCoefficients().also { it.update(FilterMode.LOWPASS_12, 19_000f, 0f, 1f, sr) }
        val filter = SvfFilter()
        var peak = 0f
        for (i in 0 until 4_800) {
            val y = filter.process((0.5 * sin(2.0 * PI * 200.0 * i / sr)).toFloat(), coefficients)
            peak = maxOf(peak, abs(y))
        }
        assertTrue("pico $peak", peak < 0.55f && peak > 0.3f)
    }

    @Test
    fun resetClearsTheState() {
        val coefficients = SvfCoefficients().also { it.update(FilterMode.LOWPASS_12, 1000f, 0.5f, 0f, sr) }
        val filter = SvfFilter()
        repeat(100) { filter.process(1f, coefficients) }
        filter.reset()
        val fresh = SvfFilter()
        assertEquals(fresh.process(0.3f, coefficients), filter.process(0.3f, coefficients), 0f)
    }
}
