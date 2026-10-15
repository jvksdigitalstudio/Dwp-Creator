package com.jvk.dwpcreator.sampler.api.params

/**
 * Registro de **todos** los parámetros e interruptores del sampler (bus y voz).
 * La persistencia y las pruebas recorren estas listas, de modo que añadir un
 * parámetro a un grupo lo incorpora automáticamente a ambas.
 */
object SamplerParams {

    val params: List<SamplerParam> = buildList {
        addAll(FxParam.entries)
        addAll(SampleControls.params)
        addAll(FilterControls.params)
        EnvelopeControls.all.forEach { addAll(it.params) }
        LfoControls.all.forEach { addAll(it.params) }
        add(LfoControls.TEMPO)
    }

    val toggles: List<SamplerToggle> = buildList {
        addAll(FxToggle.entries)
        addAll(SampleControls.toggles)
        addAll(FilterControls.toggles)
    }
}
