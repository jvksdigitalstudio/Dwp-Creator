package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.rhythm.LfoSync
import com.jvk.dwpcreator.sampler.api.state.LfoDestination
import com.jvk.dwpcreator.sampler.api.state.LfoRateMode
import com.jvk.dwpcreator.sampler.api.state.LfoShape
import com.jvk.dwpcreator.sampler.api.state.LfoState
import kotlin.math.abs
import kotlin.math.max

/**
 * Un LFO ya resuelto para el hilo de audio: frecuencia efectiva (incluido el
 * tempo), fundido en muestras y fase inicial. Inmutable; se crea al cambiar el estado.
 */
internal class LfoSettings(
    /** Ciclos por segundo (en modo one-shot, `1 / duración de la nota-ciclo`). */
    val rateHz: Float,
    val oneShot: Boolean,
    val shape: LfoShape,
    val startPhase: Float,
    /** Duración del fundido de entrada en muestras; 0 = sin fundido. */
    val fadeFrames: Float,
    val amount: Float,
    val destination: LfoDestination,
    /** Avance de fase por muestra. */
    val phasePerFrame: Float
) {
    /** `false` si el LFO no modula nada y puede saltarse. */
    val active: Boolean get() = abs(amount) > ACTIVE_THRESHOLD

    companion object {
        private const val ACTIVE_THRESHOLD = 1e-4f

        fun from(state: LfoState, tempoBpm: Float, sampleRate: Float): LfoSettings {
            val rate = when (state.rateMode) {
                LfoRateMode.TEMPO -> LfoSync.rateHz(tempoBpm, state.division)
                LfoRateMode.HERTZ, LfoRateMode.ONE_SHOT -> state.rateHz
            }
            return LfoSettings(
                rateHz = rate,
                oneShot = state.rateMode == LfoRateMode.ONE_SHOT,
                shape = state.shape,
                startPhase = state.phase.coerceIn(0f, 1f),
                fadeFrames = max(0f, state.fadeMs * 0.001f * sampleRate),
                amount = state.amount.coerceIn(-1f, 1f),
                destination = state.destination,
                phasePerFrame = rate / sampleRate
            )
        }
    }
}
