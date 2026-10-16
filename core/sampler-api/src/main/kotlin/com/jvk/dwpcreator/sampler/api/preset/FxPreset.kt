package com.jvk.dwpcreator.sampler.api.preset

import com.jvk.dwpcreator.sampler.api.params.SamplerParam
import com.jvk.dwpcreator.sampler.api.params.SamplerToggle

/**
 * Un preset: valores para los parámetros e interruptores de **un** módulo.
 * Lo que no se indique toma el valor de fábrica, de modo que un preset
 * describe siempre el módulo completo (requisito para poder detectarlo).
 */
class FxPreset(
    val name: String,
    val values: Map<out SamplerParam, Float>,
    val toggles: Map<out SamplerToggle, Boolean> = emptyMap()
)
