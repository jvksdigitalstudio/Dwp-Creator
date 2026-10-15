package com.jvk.dwpcreator.session.testing

import com.jvk.dwpcreator.sampler.api.FxStateStore
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/** Doble de [FxStateStore] en memoria que recuerda lo último guardado. */
class RecordingFxStore(private val initial: SamplerFxState = SamplerFxState()) : FxStateStore {

    var saved: SamplerFxState? = null
        private set

    override fun load(): SamplerFxState = initial

    override fun save(state: SamplerFxState) {
        saved = state
    }
}
