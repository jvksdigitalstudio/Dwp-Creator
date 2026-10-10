package com.jvk.dwpcreator.ui.state

import androidx.compose.runtime.Stable
import com.jvk.dwpcreator.audio.AudioStats
import com.jvk.dwpcreator.audio.dsp.FxModule
import com.jvk.dwpcreator.audio.dsp.FxParam
import com.jvk.dwpcreator.audio.dsp.FxPreset
import com.jvk.dwpcreator.audio.dsp.FxToggle
import com.jvk.dwpcreator.audio.dsp.SamplerFxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Todo lo que el panel del sampler necesita del exterior -- el estado del
 * bus de efectos **como flujo** y las acciones para modificarlo -- agrupado
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
    val onParamChange: (FxParam, Float) -> Unit,
    val onParamReset: (FxParam) -> Unit,
    val onToggle: (FxToggle, Boolean) -> Unit,
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
