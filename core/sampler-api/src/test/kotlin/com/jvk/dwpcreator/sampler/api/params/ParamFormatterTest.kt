package com.jvk.dwpcreator.sampler.api.params

import org.junit.Assert.assertEquals
import org.junit.Test

class ParamFormatterTest {
    private val unit = ParamScale(0f, 1f, 0f)

    private fun fmt(u: FxUnit, v: Float, scale: ParamScale = unit, labels: List<String>? = null) =
        ParamFormatter.format(u, v, scale, labels)

    @Test
    fun percentHertzAndTimeUnits() {
        assertEquals("25.00%", fmt(FxUnit.PERCENT, 0.25f))
        assertEquals("-50.00%", fmt(FxUnit.PERCENT, -0.5f))
        assertEquals("120.0 Hz", fmt(FxUnit.HERTZ, 120f))
        assertEquals("11.25 kHz", fmt(FxUnit.HERTZ, 11_250f))
        assertEquals("375.00 ms", fmt(FxUnit.MILLISECONDS, 375f))
        assertEquals("20.000 s", fmt(FxUnit.MILLISECONDS, 20_000f))
        assertEquals("1.29 sec", fmt(FxUnit.SECONDS, 1.29f))
        assertEquals("120.0 BPM", fmt(FxUnit.BPM, 120f))
    }

    @Test
    fun decibelsShowMinusInfinityAtTheScaleMinimum() {
        val scale = ParamScale(-60f, 6f, 0f)
        assertEquals("-inf dB", fmt(FxUnit.DECIBEL, -60f, scale))
        assertEquals("+0.0 dB", fmt(FxUnit.DECIBEL, 0f, scale))
        assertEquals("-6.0 dB", fmt(FxUnit.DECIBEL, -6f, scale))
    }

    @Test
    fun semitonesAndCentsAreSignedAndIntegers() {
        assertEquals("0 st", fmt(FxUnit.SEMITONES, 0.2f))
        assertEquals("+7 st", fmt(FxUnit.SEMITONES, 7f))
        assertEquals("-12 st", fmt(FxUnit.SEMITONES, -12f))
        assertEquals("+50 ct", fmt(FxUnit.CENTS, 50f))
        assertEquals("-100 ct", fmt(FxUnit.CENTS, -100f))
    }

    @Test
    fun cutoffIsShownAsAFrequency() {
        assertEquals("20.0 Hz", fmt(FxUnit.CUTOFF, 0f))
        assertEquals("640.0 Hz", fmt(FxUnit.CUTOFF, 0.5f))
        assertEquals("20.48 kHz", fmt(FxUnit.CUTOFF, 1f))
    }

    @Test
    fun degreesAreIntegers() {
        assertEquals("90\u00B0", fmt(FxUnit.DEGREES, 0.25f))
    }

    @Test
    fun choicesUseTheirLabelsAndSaturateTheIndex() {
        val labels = listOf("Sine", "Triangle", "Saw")
        assertEquals("Triangle", fmt(FxUnit.CHOICE, 1f, labels = labels))
        assertEquals("Saw", fmt(FxUnit.CHOICE, 99f, labels = labels))
        assertEquals("Sine", fmt(FxUnit.CHOICE, -3f, labels = labels))
        assertEquals("", fmt(FxUnit.CHOICE, 1f))
    }
}
