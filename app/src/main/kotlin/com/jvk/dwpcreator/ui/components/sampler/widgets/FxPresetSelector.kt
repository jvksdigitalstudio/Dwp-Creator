package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.preset.FxPresets
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.state.SamplerFxController
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.TextDim

/**
 * Selector de presets de un módulo: una etiqueta con el nombre del preset
 * activo (o **Custom** si los ajustes no coinciden con ninguno de fábrica)
 * que abre un menú con los presets y la acción "Reset module". El nombre se
 * deduce siempre del estado ([FxPresets.activeName]); la UI no guarda
 * ninguna selección propia que pueda quedar desincronizada.
 */
@Composable
fun FxPresetSelector(
    module: FxModule,
    state: SamplerFxState,
    controller: SamplerFxController,
    modifier: Modifier = Modifier
) {
    val presets = FxPresets.forModule(module)
    if (presets.isEmpty()) {
        // Módulo sin presets de fábrica (muestra, envolventes): sólo se puede reiniciar.
        FxChip(
            text = "Reset",
            color = NeonPurple,
            contentDescription = "Reiniciar ${module.title}",
            onClick = { controller.onModuleReset(module) },
            modifier = modifier
        )
        return
    }
    var open by remember { mutableStateOf(false) }
    val active = FxPresets.activeName(module, state)
    val isCustom = active == FxPresets.CUSTOM
    Box(modifier = modifier) {
        FxChip(
            text = "$active \u25BE",
            color = if (isCustom) TextDim else NeonCyan,
            contentDescription = "Preset de ${module.title}: $active",
            onClick = { open = true }
        )
        FxMenu(expanded = open, onDismiss = { open = false }) {
            for (preset in presets) {
                FxMenuItem(
                    text = preset.name,
                    color = if (preset.name == active) NeonCyan else TextDim,
                    onClick = {
                        open = false
                        controller.onPreset(module, preset)
                    }
                )
            }
            HorizontalDivider(color = MixerControlBorder)
            FxMenuItem(
                text = "Reset module",
                color = NeonPurple,
                onClick = {
                    open = false
                    controller.onModuleReset(module)
                }
            )
        }
    }
}
