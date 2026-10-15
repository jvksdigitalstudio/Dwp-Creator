package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.runtime.Composable
import com.jvk.dwpcreator.sampler.api.params.SamplerParam
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Knob ligado a **cualquier** [SamplerParam] (bus de efectos o voz): lee el
 * valor del [state], escribe vía [controller] y restaura el de fábrica con
 * doble toque. Si la escala del parámetro tiene signo (mínimo negativo), el arco
 * se dibuja desde el centro.
 */
@Composable
fun SamplerParamKnob(
    param: SamplerParam,
    state: SamplerFxState,
    controller: SamplerFxController,
    moduleEnabled: Boolean = true
) {
    val value = param.read(state)
    FxKnob(
        label = param.label,
        normalized = param.toNormalized(value),
        valueText = param.format(value),
        enabled = moduleEnabled,
        onNormalizedChange = { controller.onParamChange(param, param.fromNormalized(it)) },
        onReset = { controller.onParamReset(param) },
        bipolar = param.scale.min < 0f
    )
}
