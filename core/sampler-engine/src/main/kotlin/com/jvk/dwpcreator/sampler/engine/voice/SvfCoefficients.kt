package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.math.Dsp
import com.jvk.dwpcreator.sampler.api.math.FilterScaling
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import kotlin.math.PI
import kotlin.math.tan

/**
 * Coeficientes del filtro de variable de estado [SvfFilter] para un corte,
 * resonancia y drive dados. Una instancia por voz: se recalcula a ritmo de
 * control (cada [SamplerVoice.CONTROL_FRAMES] muestras) y la comparten los dos
 * canales.
 *
 * Topología TPT/ZDF (Zavalishin / Cytomic): estable para cualquier corte por
 * debajo de Nyquist y para modulación rápida, a diferencia de la Chamberlin
 * clásica. En **LP 24** la primera etapa lleva amortiguamiento Butterworth fijo
 * y sólo la segunda recibe la resonancia; en el resto de modos la etapa
 * primera es la única que se usa y comparte los coeficientes principales.
 */
internal class SvfCoefficients {
    var mode: FilterMode = FilterMode.LOWPASS_12
        private set

    // Etapa que se calcula siempre (la única en LP12/HP/Band/Notch; la primera en LP24).
    var firstDamping = 0f
        private set
    var firstA1 = 0f
        private set
    var firstA2 = 0f
        private set
    var firstA3 = 0f
        private set

    // Segunda etapa (sólo LP24).
    var a1 = 0f
        private set
    var a2 = 0f
        private set
    var a3 = 0f
        private set

    var drive: Boolean = false
        private set
    var driveGain = 1f
        private set
    var driveMakeup = 1f
        private set

    fun update(mode: FilterMode, cutoffHz: Float, resonance: Float, driveAmount: Float, sampleRate: Float) {
        this.mode = mode
        val g = tan(PI.toFloat() * cutoffHz.coerceIn(MIN_HZ, MAX_CUTOFF_RATIO * sampleRate) / sampleRate)
        val k = FilterScaling.damping(resonance)
        if (mode == FilterMode.LOWPASS_24) {
            setFirst(g, BUTTERWORTH_DAMPING)
            a1 = 1f / (1f + g * (g + k))
            a2 = g * a1
            a3 = g * a2
        } else {
            setFirst(g, k)
        }
        drive = driveAmount > DRIVE_THRESHOLD
        driveGain = 1f + driveAmount.coerceIn(0f, 1f) * MAX_DRIVE_GAIN
        driveMakeup = if (drive) Dsp.tanhMakeup(driveGain) else 1f
    }

    private fun setFirst(g: Float, damping: Float) {
        firstDamping = damping
        firstA1 = 1f / (1f + g * (g + damping))
        firstA2 = g * firstA1
        firstA3 = g * firstA2
    }

    companion object {
        private const val MIN_HZ = 10f

        /** Corte máximo como fracción de la frecuencia de muestreo (por debajo de Nyquist = 0,5). */
        private const val MAX_CUTOFF_RATIO = 0.45f
        private const val BUTTERWORTH_DAMPING = 1.4142135f
        private const val DRIVE_THRESHOLD = 0.001f
        private const val MAX_DRIVE_GAIN = 8f
    }
}
