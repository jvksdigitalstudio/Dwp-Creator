package com.jvk.dwpcreator.sampler.api.state

/** Envolvente de modulación: una ADSR más la cantidad bipolar con la que modula su destino. */
data class ModulationEnvelopeState(
    val adsr: AdsrState,
    /** -1..1; 0 = sin efecto. */
    val amount: Float = 0f
)
