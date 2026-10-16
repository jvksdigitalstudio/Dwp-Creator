package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.EnvelopeTarget

/** Los controles de una envolvente: A, D, S, R y, si modula algo, su cantidad. */
class EnvelopeParams(
    val target: EnvelopeTarget,
    val attack: VoiceParam,
    val decay: VoiceParam,
    val sustain: VoiceParam,
    val release: VoiceParam,
    val amount: VoiceParam?
) {
    val params: List<VoiceParam> = listOfNotNull(attack, decay, sustain, release, amount)
}
