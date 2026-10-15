package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/**
 * Contrato común de todo parámetro numérico del sampler (bus de efectos y voz).
 * Una sola definición alimenta a la UI (mapeo knob <-> valor, texto), a la
 * persistencia (clave estable [key]) y a los presets: ninguna de esas capas
 * distingue de qué módulo viene el parámetro.
 *
 * Implementaciones: [FxParam] (bus de efectos) y
 * [com.jvk.dwpcreator.sampler.api.params.VoiceParam] (síntesis por voz).
 */
interface SamplerParam {
    /** Clave estable de persistencia y de depuración. */
    val key: String
    val label: String
    val scale: ParamScale
    val unit: FxUnit

    /** Etiquetas de las opciones cuando [unit] es [FxUnit.CHOICE]; `null` en el resto. */
    val labels: List<String>?

    fun read(state: SamplerFxState): Float

    /** Escribe en [state] el [value] **ya saturado y ajustado a la rejilla** por [write]. */
    fun apply(state: SamplerFxState, value: Float): SamplerFxState

    /** Escribe [value] (saturado a la escala y, si es escalonado, ajustado a la rejilla) y devuelve el estado nuevo. */
    fun write(state: SamplerFxState, value: Float): SamplerFxState = apply(state, scale.clamp(value))

    fun toNormalized(value: Float): Float = scale.toNormalized(value)

    fun fromNormalized(normalized: Float): Float = scale.fromNormalized(normalized)

    fun format(value: Float): String = ParamFormatter.format(unit, value, scale, labels)
}
