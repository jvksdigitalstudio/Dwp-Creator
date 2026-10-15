package com.jvk.dwpcreator.session.fx

import com.jvk.dwpcreator.sampler.api.FxStateStore
import com.jvk.dwpcreator.sampler.api.SamplerPlayback
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.SamplerParam
import com.jvk.dwpcreator.sampler.api.params.SamplerToggle
import com.jvk.dwpcreator.sampler.api.preset.FxPreset
import com.jvk.dwpcreator.sampler.api.preset.FxPresets
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Estado del panel del sampler (voz y bus de efectos): lo aplica al motor al
 * instante y lo persiste con un retardo.
 */
class FxController(
    private val scope: CoroutineScope,
    private val playback: SamplerPlayback,
    private val store: FxStateStore
) {

    private val _state = MutableStateFlow(store.load().also { playback.setFxState(it) })
    val state: StateFlow<SamplerFxState> = _state.asStateFlow()

    private var saveJob: Job? = null

    fun setParam(param: SamplerParam, value: Float) = update { param.write(it, value) }

    fun resetParam(param: SamplerParam) = update { param.write(it, param.scale.default) }

    fun setToggle(toggle: SamplerToggle, enabled: Boolean) = update { toggle.write(it, enabled) }

    fun applyPreset(module: FxModule, preset: FxPreset) = update { FxPresets.apply(module, preset, it) }

    fun resetModule(module: FxModule) = update { FxPresets.reset(module, it) }

    /** Guarda de inmediato el estado actual, cancelando el guardado diferido pendiente. */
    fun flush() {
        saveJob?.cancel()
        store.save(_state.value)
    }

    /**
     * Aplica el cambio al motor de inmediato y guarda con un retardo
     * ([SAVE_DEBOUNCE_MS]): arrastrar un knob genera un valor por fotograma y
     * escribir en disco con cada uno sería un derroche.
     */
    private fun update(transform: (SamplerFxState) -> SamplerFxState) {
        val updated = _state.updateAndGet(transform)
        playback.setFxState(updated)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            store.save(updated)
        }
    }

    private companion object {
        /** Espera tras el último cambio antes de guardar. */
        const val SAVE_DEBOUNCE_MS = 400L
    }
}
