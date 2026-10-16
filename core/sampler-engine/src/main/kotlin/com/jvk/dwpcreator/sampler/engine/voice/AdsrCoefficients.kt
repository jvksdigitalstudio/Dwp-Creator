package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.state.AdsrState
import kotlin.math.exp
import kotlin.math.max

/**
 * Coeficientes por muestra de una ADSR, ya resueltos desde los tiempos de
 * [AdsrState]. Se calculan una vez al cambiar el estado (no en el hilo de audio).
 *
 * El ataque es lineal. El decaimiento y el release son **exponenciales**
 * (una sola resta y un producto por muestra) y su tiempo es lo que tardan en
 * caer 60 dB: el oído percibe así una caída uniforme, a diferencia de la rampa
 * lineal, que "cae de golpe" al final.
 */
internal class AdsrCoefficients(
    val attackStep: Float,
    val decayCoef: Float,
    val sustain: Float,
    val releaseCoef: Float
) {
    companion object {
        /** `ln(0,001)`: caída de 60 dB. */
        private const val LN_MINUS_60_DB = -6.907755f

        fun from(state: AdsrState, sampleRate: Float): AdsrCoefficients = AdsrCoefficients(
            attackStep = 1f / max(1f, state.attackMs * 0.001f * sampleRate),
            decayCoef = coefficient(state.decayMs, sampleRate),
            sustain = state.sustain.coerceIn(0f, 1f),
            releaseCoef = coefficient(state.releaseMs, sampleRate)
        )

        private fun coefficient(ms: Float, sampleRate: Float): Float =
            exp(LN_MINUS_60_DB / max(1f, ms * 0.001f * sampleRate))
    }
}
