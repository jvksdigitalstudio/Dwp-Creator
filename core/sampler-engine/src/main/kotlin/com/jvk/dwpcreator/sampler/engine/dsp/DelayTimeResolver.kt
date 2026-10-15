package com.jvk.dwpcreator.sampler.engine.dsp

import com.jvk.dwpcreator.sampler.api.rhythm.DelaySync
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/**
 * Tiempo de retardo efectivo del delay maestro: el valor manual del panel o,
 * con la sincronía activada, la división rítmica resuelta contra el BPM
 * propio del módulo y acotada a la capacidad real de la línea de retardo
 * ([StereoDelayEffect.MAX_DELAY_SECONDS]).
 *
 * Separado de [DelaySync] (tabla de divisiones, parte del modelo de
 * parámetros) porque depende del límite físico del efecto, que es un detalle
 * del motor de audio.
 */
internal object DelayTimeResolver {

    /** Margen (ms) que se deja bajo el máximo de la línea de retardo. */
    private const val HEADROOM_MS = 10f

    fun effectiveTimeMs(state: SamplerFxState): Float =
        if (state.delaySync) {
            DelaySync.resolveMs(
                state.delayBpm,
                state.delayDivision,
                StereoDelayEffect.MAX_DELAY_SECONDS * 1000f - HEADROOM_MS
            )
        } else {
            state.delayTimeMs
        }
}
