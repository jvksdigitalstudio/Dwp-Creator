package com.jvk.dwpcreator.sampler.android

import android.content.Context
import com.jvk.dwpcreator.sampler.api.FxStateStore
import com.jvk.dwpcreator.sampler.api.params.SamplerParams
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/**
 * Persistencia del estado del panel del sampler (bus de efectos y voz) entre
 * sesiones (`SharedPreferences`). Genérica sobre el registro [SamplerParams]:
 * añadir un parámetro o interruptor a cualquier grupo lo persiste solo, sin
 * tocar esta clase. Las claves son las de cada parámetro ([com.jvk.dwpcreator.sampler.api.params.SamplerParam.key]),
 * estables; cualquier valor ausente o fuera de rango se sustituye por el de
 * fábrica, así que un archivo de preferencias antiguo o corrupto nunca produce
 * un estado inválido.
 */
class SamplerFxPreferences(context: Context) : FxStateStore {

    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): SamplerFxState {
        var state = SamplerFxState()
        for (param in SamplerParams.params) {
            if (prefs.contains(param.key)) {
                val stored = prefs.getFloat(param.key, param.scale.default)
                if (!stored.isNaN()) state = param.write(state, stored)
            }
        }
        for (toggle in SamplerParams.toggles) {
            if (prefs.contains(toggle.key)) {
                state = toggle.write(state, prefs.getBoolean(toggle.key, toggle.default))
            }
        }
        return state
    }

    override fun save(state: SamplerFxState) {
        val editor = prefs.edit()
        for (param in SamplerParams.params) editor.putFloat(param.key, param.read(state))
        for (toggle in SamplerParams.toggles) editor.putBoolean(toggle.key, toggle.read(state))
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "sampler_fx"
    }
}
