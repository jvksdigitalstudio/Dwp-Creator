package com.jvk.dwpcreator.sampler.api.state

/**
 * Estado completo, inmutable, de la síntesis por voz: muestra, filtro, las tres
 * envolventes (amplitud, filtro, afinación) y dos LFO. Vive dentro de
 * [com.jvk.dwpcreator.sampler.api.state.SamplerFxState.voice] y se resuelve a
 * coeficientes de audio en [VoiceSettings].
 */
data class VoiceState(
    val sample: SampleState = SampleState(),
    val filter: FilterState = FilterState(),
    val ampEnvelope: AdsrState = AdsrState.AMP,
    val filterEnvelope: ModulationEnvelopeState = ModulationEnvelopeState(AdsrState.FILTER),
    val pitchEnvelope: ModulationEnvelopeState = ModulationEnvelopeState(AdsrState.PITCH),
    val lfo1: LfoState = LfoState.LFO_1,
    val lfo2: LfoState = LfoState.LFO_2,
    /** Tempo compartido por los dos LFO cuando van sincronizados. */
    val tempoBpm: Float = 120f
)
