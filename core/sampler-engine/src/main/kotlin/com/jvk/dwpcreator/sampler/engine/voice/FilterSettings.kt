package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.math.FilterScaling
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import com.jvk.dwpcreator.sampler.api.state.FilterState
import com.jvk.dwpcreator.sampler.api.state.LfoDestination
import kotlin.math.abs

/**
 * Filtro de voz ya resuelto para el hilo de audio. [bypass] indica que el
 * filtro no puede alterar la señal con estos ajustes (apagado, o pasa-bajos
 * totalmente abierto sin resonancia, drive ni modulación) y se salta por completo:
 * con el estado de fábrica, el sonido de la muestra no se toca.
 */
internal class FilterSettings(
    val mode: FilterMode,
    /** Octavas por encima de [FilterScaling.MIN_HZ] del corte base. */
    val cutoffOctaves: Float,
    val resonance: Float,
    val drive: Float,
    val keyTrack: Float,
    val velocityTrack: Float,
    val bypass: Boolean
) {
    companion object {
        private const val OPEN_THRESHOLD = 0.999f
        private const val ZERO = 0.001f

        /**
         * @param envelopeAmount cantidad de la envolvente de filtro (-1..1).
         * @param lfos LFO activos del programa: si alguno modula el corte o la
         *   resonancia, el filtro no puede puentearse.
         */
        fun from(state: FilterState, envelopeAmount: Float, lfos: List<LfoSettings>): FilterSettings {
            val modulated = abs(envelopeAmount) > ZERO || lfos.any {
                it.active && (it.destination == LfoDestination.FILTER || it.destination == LfoDestination.RESONANCE)
            }
            val transparent = state.mode.transparentWhenOpen &&
                state.cutoff >= OPEN_THRESHOLD &&
                state.resonance <= ZERO &&
                state.drive <= ZERO &&
                abs(state.keyTrack) <= ZERO &&
                abs(state.velocityTrack) <= ZERO &&
                !modulated
            return FilterSettings(
                mode = state.mode,
                cutoffOctaves = FilterScaling.cutoffOctaves(state.cutoff),
                resonance = state.resonance,
                drive = state.drive,
                keyTrack = state.keyTrack,
                velocityTrack = state.velocityTrack,
                bypass = !state.enabled || transparent
            )
        }
    }
}
