package com.jvk.dwpcreator.ui.state

import androidx.compose.runtime.Stable
import com.jvk.dwpcreator.sampler.api.AudioStats
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.SamplerParam
import com.jvk.dwpcreator.sampler.api.params.SamplerToggle
import com.jvk.dwpcreator.sampler.api.preset.FxPreset
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Todo lo que el panel del sampler necesita del exterior -- el estado del
 * panel (bus de efectos y voz) **como flujo** y las acciones para modificarlo -- agrupado
 * en un solo objeto.
 *
 * Que [state] sea un `StateFlow` (y no un valor pasado por parámetro) es una
 * decisión de rendimiento: el panel lo recoge él mismo, así que mover un knob
 * (decenas de cambios por segundo) recompone **sólo el panel**; si el valor
 * subiera por `MainActivity -> MainScreen`, cada fotograma del arrastre
 * recompondría la pantalla entera. [NONE] es el valor neutro para
 * previsualizaciones y pruebas de UI.
 */
@Stable
class SamplerFxController(
    val state: StateFlow<SamplerFxState>,
    val onParamChange: (SamplerParam, Float) -> Unit,
    val onParamReset: (SamplerParam) -> Unit,
    val onToggle: (SamplerToggle, Boolean) -> Unit,
    val onPreset: (FxModule, FxPreset) -> Unit,
    val onModuleReset: (FxModule) -> Unit,
    val readPeaks: () -> Pair<Float, Float>,
    val readStats: () -> AudioStats
) {
    companion object {
        val NONE = SamplerFxController(
            state = MutableStateFlow(SamplerFxState()),
            onParamChange = { _, _ -> },
            onParamReset = {},
            onToggle = { _, _ -> },
            onPreset = { _, _ -> },
            onModuleReset = {},
            readPeaks = { Pair(0f, 0f) },
            readStats = { AudioStats() }
        )
    }
}
