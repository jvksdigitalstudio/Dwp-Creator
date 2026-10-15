package com.jvk.dwpcreator.sampler.api.params

/** Los controles de un LFO. */
class LfoParams(
    val index: Int,
    val mode: VoiceParam,
    val rate: VoiceParam,
    val division: VoiceParam,
    val shape: VoiceParam,
    val phase: VoiceParam,
    val fade: VoiceParam,
    val amount: VoiceParam,
    val destination: VoiceParam
) {
    val params: List<VoiceParam> = listOf(mode, rate, division, shape, phase, fade, amount, destination)
}
