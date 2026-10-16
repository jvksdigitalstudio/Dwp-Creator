package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/** Interruptor de dos estados del sampler. Misma idea que [SamplerParam]. */
interface SamplerToggle {
    val key: String
    val default: Boolean
    fun read(state: SamplerFxState): Boolean
    fun write(state: SamplerFxState, value: Boolean): SamplerFxState
}
