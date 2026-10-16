package com.jvk.dwpcreator.sampler.api.curve

import com.jvk.dwpcreator.sampler.api.math.Dsp
import com.jvk.dwpcreator.sampler.api.state.LfoShape

/**
 * Formas de onda del LFO como funciones puras de la fase. Las usan el oscilador
 * de audio ([LfoOscillator]) y la previsualización de la UI, de modo que lo que
 * se dibuja es exactamente lo que suena.
 */
object LfoWaveforms {

    /**
     * Valor bipolar (-1..1) de [shape] en [phase] (0..1). [held] y [next] son los
     * valores aleatorios del ciclo actual y del siguiente: sólo los usan las
     * formas aleatorias.
     */
    fun value(shape: LfoShape, phase: Float, held: Float = 0f, next: Float = 0f): Float = when (shape) {
        LfoShape.SINE -> Dsp.sin2Pi(phase)
        LfoShape.TRIANGLE -> when {
            phase < 0.25f -> 4f * phase
            phase < 0.75f -> 2f - 4f * phase
            else -> 4f * phase - 4f
        }
        LfoShape.SAW -> 1f - 2f * phase
        LfoShape.SQUARE -> if (phase < 0.5f) 1f else -1f
        LfoShape.SAMPLE_HOLD -> held
        LfoShape.SMOOTH_RANDOM -> held + (next - held) * (phase * phase * (3f - 2f * phase))
    }
}
