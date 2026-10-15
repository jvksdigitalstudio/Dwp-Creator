package com.jvk.dwpcreator.sampler.api

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/**
 * Persistencia del estado del bus de efectos y de la voz entre sesiones.
 *
 * Contrato independiente del medio (hoy `SharedPreferences`, en
 * `sampler-android`), para que la lógica de aplicación no conozca Android.
 */
interface FxStateStore {

    /** Estado guardado; los parámetros ausentes o corruptos toman su valor por defecto. */
    fun load(): SamplerFxState

    /** Guarda [state] completo. */
    fun save(state: SamplerFxState)
}
