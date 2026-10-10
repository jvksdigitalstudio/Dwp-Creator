package com.jvk.dwpcreator.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FxLevelMeterTest {

    @Test
    fun meterScaleSpansMinus60ToZeroDb() {
        assertEquals(0f, meterPosition(0f), 0f)
        assertEquals(0f, meterPosition(0.0005f), 0f) // < -60 dB
        assertEquals(1f, meterPosition(1f), 1e-6f)   // 0 dBFS
        assertEquals(1f, meterPosition(2f), 0f)      // por encima: saturado
        assertEquals(0.5f, meterPosition(0.0316228f), 1e-3f) // -30 dB
    }
}
