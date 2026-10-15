package com.jvk.dwpcreator.sampler.engine.dsp

import com.jvk.dwpcreator.sampler.api.math.Dsp

/** Pasa-bajos de un polo (6 dB/oct). */
internal class OnePoleLowPass {
    private var z = 0f

    fun process(x: Float, coef: Float): Float {
        z = Dsp.flush(z + coef * (x - z))
        return z
    }

    fun clear() {
        z = 0f
    }
}
