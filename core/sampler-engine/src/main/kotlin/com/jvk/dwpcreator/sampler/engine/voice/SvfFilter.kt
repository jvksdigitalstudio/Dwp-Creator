package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.math.Dsp
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import kotlin.math.tanh

/**
 * Un canal del filtro de variable de estado de la voz (ver [SvfCoefficients]):
 * pasa-bajos 12/24 dB, pasa-altos, pasa-banda (pico unitario) y notch, con
 * saturación opcional (*drive*) **antes** del filtro. Guarda sólo el estado de
 * sus dos etapas; los coeficientes los aporta quien lo llama.
 */
internal class SvfFilter {
    private var ic1a = 0f
    private var ic2a = 0f
    private var ic1b = 0f
    private var ic2b = 0f

    fun reset() {
        ic1a = 0f
        ic2a = 0f
        ic1b = 0f
        ic2b = 0f
    }

    fun process(input: Float, c: SvfCoefficients): Float {
        val x = if (c.drive) tanh(input * c.driveGain) * c.driveMakeup else input

        // Etapa 1
        val v3a = x - ic2a
        val v1a = c.firstA1 * ic1a + c.firstA2 * v3a
        val v2a = ic2a + c.firstA2 * ic1a + c.firstA3 * v3a
        ic1a = Dsp.flush(2f * v1a - ic1a)
        ic2a = Dsp.flush(2f * v2a - ic2a)

        return when (c.mode) {
            FilterMode.LOWPASS_12 -> v2a
            FilterMode.HIGHPASS_12 -> x - c.firstDamping * v1a - v2a
            FilterMode.BANDPASS -> c.firstDamping * v1a
            FilterMode.NOTCH -> x - c.firstDamping * v1a
            FilterMode.LOWPASS_24 -> {
                val v3b = v2a - ic2b
                val v1b = c.a1 * ic1b + c.a2 * v3b
                val v2b = ic2b + c.a2 * ic1b + c.a3 * v3b
                ic1b = Dsp.flush(2f * v1b - ic1b)
                ic2b = Dsp.flush(2f * v2b - ic2b)
                v2b
            }
        }
    }
}
