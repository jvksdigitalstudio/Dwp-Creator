package com.jvk.dwpcreator.sampler.api.curve

import com.jvk.dwpcreator.sampler.api.math.FilterScaling
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterScalingAndResponseTest {

    @Test
    fun cutoffSpansTenOctavesFromTwentyHertz() {
        assertEquals(20f, FilterScaling.cutoffHz(0f), 0.001f)
        assertEquals(640f, FilterScaling.cutoffHz(0.5f), 0.5f)
        assertEquals(20_480f, FilterScaling.cutoffHz(1f), 1f)
        assertEquals(20f, FilterScaling.cutoffHz(-3f), 0.001f)
        assertEquals(5f, FilterScaling.cutoffOctaves(0.5f), 1e-6f)
    }

    @Test
    fun resonanceSpansButterworthToQTen() {
        assertEquals(0.7071f, FilterScaling.q(0f), 1e-4f)
        assertEquals(10f, FilterScaling.q(1f), 1e-3f)
        assertEquals(1f / 10f, FilterScaling.damping(1f), 1e-4f)
        assertTrue(FilterScaling.q(0.5f) > FilterScaling.q(0.4f))
    }

    @Test
    fun lowpassIsMinusThreeDecibelsAtTheCutoffWithoutResonance() {
        assertEquals(-3.01f, FilterResponse.magnitudeDb(FilterMode.LOWPASS_12, 1000f, 0f, 1000f), 0.05f)
        assertEquals(-6.02f, FilterResponse.magnitudeDb(FilterMode.LOWPASS_24, 1000f, 0f, 1000f), 0.05f)
    }

    @Test
    fun resonanceRaisesThePeakToTheQValue() {
        assertEquals(20f, FilterResponse.magnitudeDb(FilterMode.LOWPASS_12, 1000f, 1f, 1000f), 0.05f)
    }

    @Test
    fun bandpassPeaksAtUnityAndNotchRejectsTheCutoff() {
        assertEquals(0f, FilterResponse.magnitudeDb(FilterMode.BANDPASS, 1000f, 0.4f, 1000f), 0.01f)
        assertTrue(FilterResponse.magnitudeDb(FilterMode.NOTCH, 1000f, 0.4f, 1000f) < -60f)
    }

    @Test
    fun highpassRejectsLowFrequencies() {
        assertTrue(FilterResponse.magnitudeDb(FilterMode.HIGHPASS_12, 1000f, 0f, 100f) < -35f)
        assertEquals(0f, FilterResponse.magnitudeDb(FilterMode.HIGHPASS_12, 1000f, 0f, 20_000f), 0.1f)
    }
}
