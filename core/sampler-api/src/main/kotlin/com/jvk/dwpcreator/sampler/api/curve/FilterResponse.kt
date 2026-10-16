package com.jvk.dwpcreator.sampler.api.curve

import com.jvk.dwpcreator.sampler.api.math.FilterScaling
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Módulo de la respuesta en frecuencia del filtro de voz (prototipo analógico
 * de segundo orden, el mismo que discretiza [SvfFilter]). Es una función pura:
 * la UI la usa para dibujar la curva del filtro en tiempo real y las pruebas
 * comprueban con ella que el filtro de audio se comporta como dibuja la UI.
 */
object FilterResponse {

    /** Magnitud en dB (suelo -120 dB) de [mode] con [cutoffHz] y [resonance] a la frecuencia [frequencyHz]. */
    fun magnitudeDb(mode: FilterMode, cutoffHz: Float, resonance: Float, frequencyHz: Float): Float {
        val w = frequencyHz / cutoffHz
        val w2 = w * w
        val k = FilterScaling.damping(resonance)
        val magnitude = when (mode) {
            FilterMode.LOWPASS_12 -> 1f / denominator(w2, k, w)
            FilterMode.LOWPASS_24 -> 1f / (denominator(w2, BUTTERWORTH_DAMPING, w) * denominator(w2, k, w))
            FilterMode.HIGHPASS_12 -> w2 / denominator(w2, k, w)
            FilterMode.BANDPASS -> k * w / denominator(w2, k, w)
            FilterMode.NOTCH -> abs(1f - w2) / denominator(w2, k, w)
        }
        return 20f * log10(max(magnitude, MIN_MAGNITUDE))
    }

    private fun denominator(w2: Float, k: Float, w: Float): Float {
        val real = 1f - w2
        val imaginary = k * w
        return sqrt(real * real + imaginary * imaginary)
    }

    private const val BUTTERWORTH_DAMPING = 1.4142135f
    private const val MIN_MAGNITUDE = 1e-6f
}
