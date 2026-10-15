package com.jvk.dwpcreator.sampler.api.preset

import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import kotlin.math.abs

/**
 * Operaciones sobre los presets de fábrica y el reinicio de módulo. **Todo son
 * funciones puras sobre [SamplerFxState]**: sin estado propio, sin Android,
 * probables en JVM. Los datos de los presets están en [FxPresetCatalog] (bus de
 * efectos) y [VoicePresetCatalog] (voz).
 *
 * - [apply]: carga un preset y **enciende** el módulo (quien elige un preset
 *   quiere oírlo).
 * - [activeName]: nombre del preset que coincide con los ajustes actuales o
 *   [CUSTOM]. La comparación es sobre la posición normalizada del knob
 *   (tolerancia [MATCH_TOLERANCE]), la misma que ve el usuario, así que el
 *   redondeo de punto flotante o de la persistencia no rompe la coincidencia.
 * - [reset]: devuelve los ajustes del módulo a los de fábrica **sin cambiar
 *   su encendido**.
 */
object FxPresets {

    const val CUSTOM = "Custom"
    private const val MATCH_TOLERANCE = 0.002f

    /** Presets de fábrica de [module], en el orden en que se muestran. */
    fun forModule(module: FxModule): List<FxPreset> = when (module) {
        FxModule.DRIVE_A -> FxPresetCatalog.DRIVE_A_PRESETS
        FxModule.DRIVE_B -> FxPresetCatalog.DRIVE_B_PRESETS
        FxModule.CHORUS -> FxPresetCatalog.CHORUS_PRESETS
        FxModule.DELAY -> FxPresetCatalog.DELAY_PRESETS
        FxModule.REVERB -> FxPresetCatalog.REVERB_PRESETS
        FxModule.FILTER -> VoicePresetCatalog.FILTER_PRESETS
        FxModule.LFO_1 -> VoicePresetCatalog.LFO_1_PRESETS
        FxModule.LFO_2 -> VoicePresetCatalog.LFO_2_PRESETS
        FxModule.SAMPLE, FxModule.AMP_ENV, FxModule.FILTER_ENV, FxModule.PITCH_ENV -> emptyList()
    }

    /** Aplica [preset] a [module] y enciende el módulo. Los ajustes no mencionados vuelven al valor de fábrica. */
    fun apply(module: FxModule, preset: FxPreset, state: SamplerFxState): SamplerFxState {
        var next = state
        for (param in module.params) next = param.write(next, preset.values[param] ?: param.scale.default)
        for (toggle in module.toggles) next = toggle.write(next, preset.toggles[toggle] ?: toggle.default)
        return module.power?.write(next, true) ?: next
    }

    /** Devuelve los ajustes de [module] a los de fábrica; el encendido no cambia. */
    fun reset(module: FxModule, state: SamplerFxState): SamplerFxState {
        var next = state
        for (param in module.params) next = param.write(next, param.scale.default)
        for (toggle in module.toggles) next = toggle.write(next, toggle.default)
        return next
    }

    /** `true` si los ajustes de [module] en [state] coinciden con [preset]. */
    fun matches(module: FxModule, preset: FxPreset, state: SamplerFxState): Boolean {
        for (param in module.params) {
            val expected = preset.values[param] ?: param.scale.default
            if (abs(param.toNormalized(param.read(state)) - param.toNormalized(expected)) > MATCH_TOLERANCE) return false
        }
        for (toggle in module.toggles) {
            if (toggle.read(state) != (preset.toggles[toggle] ?: toggle.default)) return false
        }
        return true
    }

    /** Nombre del preset activo de [module] o [CUSTOM] si los ajustes no coinciden con ninguno. */
    fun activeName(module: FxModule, state: SamplerFxState): String =
        forModule(module).firstOrNull { matches(module, it, state) }?.name ?: CUSTOM
}
