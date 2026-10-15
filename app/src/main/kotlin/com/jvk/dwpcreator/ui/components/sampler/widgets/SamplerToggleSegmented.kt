package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.runtime.Composable
import com.jvk.dwpcreator.sampler.api.params.SamplerToggle
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.state.SamplerFxController

/** Selector de dos opciones ligado a un [SamplerToggle]: [offLabel] cuando está apagado, [onLabel] cuando está encendido. */
@Composable
fun SamplerToggleSegmented(
    toggle: SamplerToggle,
    state: SamplerFxState,
    controller: SamplerFxController,
    offLabel: String,
    onLabel: String
) {
    FxSegmented(
        options = listOf(offLabel, onLabel),
        selectedIndex = if (toggle.read(state)) 1 else 0,
        onSelect = { controller.onToggle(toggle, it == 1) }
    )
}
