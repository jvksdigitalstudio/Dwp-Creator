package com.jvk.dwpcreator.sampler.api.params

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamScaleTest {

    @Test
    fun linearScaleRoundTrips() {
        val scale = ParamScale(-24f, 24f, 0f)
        for (n in listOf(0f, 0.25f, 0.5f, 0.8f, 1f)) assertEquals(n, scale.toNormalized(scale.fromNormalized(n)), 1e-5f)
        assertEquals(0f, scale.fromNormalized(0.5f), 1e-5f)
    }

    @Test
    fun logScaleMultipliesByTheSameFactorPerStep() {
        val scale = ParamScale(20f, 20_000f, 1000f, FxCurve.LOG)
        assertEquals(20f, scale.fromNormalized(0f), 1e-3f)
        assertEquals(20_000f, scale.fromNormalized(1f), 1f)
        assertEquals(632.46f, scale.fromNormalized(0.5f), 0.1f) // media geométrica
        for (n in listOf(0.1f, 0.4f, 0.9f)) assertEquals(n, scale.toNormalized(scale.fromNormalized(n)), 1e-4f)
    }

    @Test
    fun squaredScaleGivesFineResolutionNearTheMinimum() {
        val scale = ParamScale(0f, 10_000f, 0f, FxCurve.SQUARED)
        assertEquals(0f, scale.fromNormalized(0f), 0f)
        assertEquals(2_500f, scale.fromNormalized(0.5f), 0.01f)
        assertEquals(10_000f, scale.fromNormalized(1f), 0.01f)
        for (n in listOf(0.1f, 0.4f, 0.9f)) assertEquals(n, scale.toNormalized(scale.fromNormalized(n)), 1e-5f)
    }

    @Test
    fun steppedScaleSnapsToTheGridAndIsIdempotent() {
        val scale = ParamScale(-24f, 24f, 0f, steps = 48)
        assertEquals(3f, scale.clamp(3.4f), 1e-5f)
        assertEquals(4f, scale.clamp(3.6f), 1e-5f)
        assertEquals(-24f, scale.clamp(-99f), 0f)
        assertEquals(24f, scale.clamp(99f), 0f)
        for (n in listOf(0.13f, 0.5f, 0.77f)) {
            val v = scale.fromNormalized(n)
            assertEquals(v, scale.fromNormalized(scale.toNormalized(v)), 1e-5f)
        }
    }

    @Test
    fun continuousScaleClampOnlySaturates() {
        val scale = ParamScale(0f, 1f, 0.5f)
        assertEquals(0.123f, scale.clamp(0.123f), 0f)
        assertEquals(1f, scale.clamp(7f), 0f)
    }

    @Test
    fun outOfRangeInputsAreSaturatedWhenNormalizing() {
        val scale = ParamScale(1f, 9f, 5f)
        assertEquals(0f, scale.toNormalized(-100f), 0f)
        assertEquals(1f, scale.toNormalized(100f), 0f)
        assertEquals(1f, scale.fromNormalized(5f) / 9f, 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aDegenerateRangeIsRejected() {
        ParamScale(1f, 1f, 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aLogScaleNeedsAPositiveMinimum() {
        ParamScale(0f, 10f, 1f, FxCurve.LOG)
    }

    @Test(expected = IllegalArgumentException::class)
    fun theDefaultMustLieInsideTheRange() {
        ParamScale(0f, 1f, 2f)
    }

    @Test
    fun aValidScaleKeepsItsDeclaredProperties() {
        val scale = ParamScale(2f, 8f, 4f, FxCurve.LINEAR, 3)
        assertTrue(scale.min == 2f && scale.max == 8f && scale.default == 4f && scale.steps == 3)
    }
}
