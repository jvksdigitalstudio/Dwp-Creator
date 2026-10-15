package com.jvk.dwpcreator.session.playback

import com.jvk.dwpcreator.ui.state.SampleMixerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mini-mezclador por muestra (Mute / Solo / Pan / Volumen).
 *
 * Es solo para tocar y previsualizar en vivo: nada de esto se exporta (ver
 * [SampleMixerState]). El mapa solo contiene las muestras que se apartaron de
 * [SampleMixerState.DEFAULT]; [stateFor] resuelve el resto.
 */
class MixerController {

    private val _states = MutableStateFlow<Map<Int, SampleMixerState>>(emptyMap())
    val states: StateFlow<Map<Int, SampleMixerState>> = _states.asStateFlow()

    /** Ajustes de la muestra [index] (los de fábrica si nunca se tocaron). */
    fun stateFor(index: Int): SampleMixerState = _states.value[index] ?: SampleMixerState.DEFAULT

    /**
     * Si la muestra [index] debe sonar. Con alguna muestra en Solo suenan
     * únicamente las que están en Solo; si no hay ninguna, suenan todas las que
     * no estén silenciadas.
     */
    fun isAudible(index: Int): Boolean {
        val all = _states.value
        val anySolo = all.values.any { it.solo }
        val state = all[index] ?: SampleMixerState.DEFAULT
        return if (anySolo) state.solo else !state.muted
    }

    fun toggleMute(index: Int) = update(index) { it.copy(muted = !it.muted) }

    fun toggleSolo(index: Int) = update(index) { it.copy(solo = !it.solo) }

    fun setPan(index: Int, pan: Float) = update(index) { it.copy(pan = pan.coerceIn(-1f, 1f)) }

    fun setVolume(index: Int, volume: Float) = update(index) { it.copy(volume = volume.coerceIn(0f, 1f)) }

    /**
     * Vuelve todo a fábrica. Se llama al cargar un instrumento nuevo: los
     * índices se reutilizan entre instrumentos, así que un ajuste del anterior
     * no tiene sentido aplicado a la muestra que ahora ocupa esa posición.
     */
    fun reset() {
        _states.value = emptyMap()
    }

    private fun update(index: Int, transform: (SampleMixerState) -> SampleMixerState) {
        val updated = transform(stateFor(index))
        _states.value = if (updated.isDefault()) _states.value - index else _states.value + (index to updated)
    }
}
