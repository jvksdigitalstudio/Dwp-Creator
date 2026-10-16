package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.sampler.api.state.VoiceState

/** Interruptor de la síntesis por voz. Una sola clase para todos los grupos, igual que [VoiceParam]. */
class VoiceToggle(
    override val key: String,
    override val default: Boolean,
    private val reader: (VoiceState) -> Boolean,
    private val writer: (VoiceState, Boolean) -> VoiceState
) : SamplerToggle {

    override fun read(state: SamplerFxState): Boolean = reader(state.voice)

    override fun write(state: SamplerFxState, value: Boolean): SamplerFxState =
        state.withVoice { writer(it, value) }
}
