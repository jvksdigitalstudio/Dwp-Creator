package com.jvk.dwpcreator.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los descriptores [FxParam]/[FxToggle] son la fuente única de verdad de DSP, UI y persistencia: deben ser coherentes. */
class SamplerFxStateTest {

    @Test
    fun everyParamDefaultMatchesTheStateDefaultAndLiesInRange() {
        val defaults = SamplerFxState()
        for (param in FxParam.entries) {
            assertEquals("${param.name} default vs estado", param.default, param.read(defaults), 1e-6f)
            assertTrue("${param.name} default dentro de rango", param.default in param.min..param.max)
        }
    }

    @Test
    fun logParamsHavePositiveMinimum() {
        for (param in FxParam.entries.filter { it.curve == FxCurve.LOG }) {
            assertTrue("${param.name} LOG requiere min > 0", param.min > 0f)
        }
    }

    @Test
    fun normalizationRoundTripsAtEveryPosition() {
        for (param in FxParam.entries) {
            for (n in listOf(0f, 0.1f, 0.25f, 0.5f, 0.77f, 1f)) {
                val value = param.fromNormalized(n)
                if (param.steps > 0) {
                    // Escalonado: el valor cae en la rejilla y reconvertirlo es idempotente.
                    assertEquals("${param.name} @ $n (idempotente)", value, param.fromNormalized(param.toNormalized(value)), 1e-4f)
                } else {
                    assertEquals("${param.name} @ $n", n, param.toNormalized(value), 1e-4f)
                }
            }
            assertEquals(param.min, param.fromNormalized(0f), 1e-4f * param.max)
            assertEquals(param.max, param.fromNormalized(1f), 1e-4f * param.max)
        }
    }

    @Test
    fun writeClampsToRangeAndOnlyTouchesItsOwnField() {
        val base = SamplerFxState()
        val high = FxParam.REVERB_DECAY.write(base, 9999f)
        assertEquals(FxParam.REVERB_DECAY.max, high.reverbDecaySec, 0f)
        val low = FxParam.REVERB_DECAY.write(base, -5f)
        assertEquals(FxParam.REVERB_DECAY.min, low.reverbDecaySec, 0f)
        assertEquals(base.copy(reverbDecaySec = FxParam.REVERB_DECAY.max), high)
    }

    @Test
    fun togglesDefaultMatchStateDefaultAndWriteIndependently() {
        val defaults = SamplerFxState()
        for (toggle in FxToggle.entries) {
            assertEquals("${toggle.name} default", toggle.default, toggle.read(defaults))
        }
        val on = FxToggle.REVERB.write(defaults, true)
        assertTrue(on.reverbEnabled)
        assertFalse(on.delayEnabled)
        assertFalse(on.chorusEnabled)
    }

    @Test
    fun defaultStateLeavesTheSoundUntouched() {
        val s = SamplerFxState()
        assertEquals(0f, s.masterVolumeDb, 0f)
        assertFalse(s.driveAEnabled || s.driveBEnabled || s.chorusEnabled || s.delayEnabled || s.reverbEnabled)
        assertFalse(s.monophonic)
    }

    @Test
    fun formatMatchesDirectWaveStyle() {
        assertEquals("25.00%", FxParam.REVERB_ROOM.format(0.25f))
        assertEquals("11.25 kHz", FxParam.REVERB_DAMP.format(11_250f))
        assertEquals("538.6 Hz", FxParam.DELAY_LOW_CUT.format(538.6f))
        assertEquals("1.29 sec", FxParam.REVERB_DECAY.format(1.29f))
        assertEquals("3.09 ms", FxParam.CHORUS_DELAY.format(3.09f))
        assertEquals("-inf dB", FxParam.MASTER_VOLUME.format(FxParam.MASTER_VOLUME.min))
        assertEquals("+0.0 dB", FxParam.MASTER_VOLUME.format(0f))
    }

    @Test
    fun steppedParamSnapsToItsGridOnWriteAndNormalize() {
        val division = FxParam.DELAY_DIVISION
        assertEquals(9, division.steps)
        assertEquals(4, division.write(SamplerFxState(), 3.6f).delayDivision)
        assertEquals(0, division.write(SamplerFxState(), -3f).delayDivision)
        assertEquals(9, division.write(SamplerFxState(), 99f).delayDivision)
        // Cada posición del knob cae exactamente en un índice entero.
        for (i in 0..9) assertEquals(i.toFloat(), division.fromNormalized(i / 9f), 1e-4f)
    }

    @Test
    fun newParamsFormatForDisplay() {
        assertEquals("120.0 BPM", FxParam.DELAY_BPM.format(120f))
        assertEquals("1/8.", FxParam.DELAY_DIVISION.format(4f))
        assertEquals("25.00 ms", FxParam.REVERB_PREDELAY.format(25f))
        assertEquals("150.00%", FxParam.REVERB_WIDTH.format(1.5f))
        assertEquals("20.00 kHz", FxParam.DRIVE_A_TONE.format(20_000f))
    }
}
