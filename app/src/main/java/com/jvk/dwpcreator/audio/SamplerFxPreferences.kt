package com.jvk.dwpcreator.audio

import android.content.Context
import com.jvk.dwpcreator.audio.dsp.FxParam
import com.jvk.dwpcreator.audio.dsp.FxToggle
import com.jvk.dwpcreator.audio.dsp.SamplerFxState

/**
 * Persistencia del estado del bus de efectos entre sesiones
 * (`SharedPreferences`). Genérica sobre [FxParam]/[FxToggle]: añadir un
 * parámetro nuevo a esos enums lo persiste solo, sin tocar esta clase. Las
 * claves son los `name` de los enums (estables); cualquier valor ausente o
 * fuera de rango se sustituye por el de fábrica, así que un archivo de
 * preferencias antiguo o corrupto nunca produce un estado inválido.
 */
class SamplerFxPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): SamplerFxState {
        var state = SamplerFxState()
        for (param in FxParam.entries) {
            if (prefs.contains(param.name)) {
                val stored = prefs.getFloat(param.name, param.default)
                if (!stored.isNaN()) state = param.write(state, stored)
            }
        }
        for (toggle in FxToggle.entries) {
            if (prefs.contains(toggle.name)) {
                state = toggle.write(state, prefs.getBoolean(toggle.name, toggle.default))
            }
        }
        return state
    }

    fun save(state: SamplerFxState) {
        val editor = prefs.edit()
        for (param in FxParam.entries) editor.putFloat(param.name, param.read(state))
        for (toggle in FxToggle.entries) editor.putBoolean(toggle.name, toggle.read(state))
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "sampler_fx"
    }
}
