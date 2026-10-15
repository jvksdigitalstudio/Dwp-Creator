package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.sampler.api.params.SamplerParam
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.state.SamplerFxController
import com.jvk.dwpcreator.ui.theme.MixerCaptionText
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.TextDim
import kotlin.math.roundToInt

/**
 * Selector de una opción ligado a un parámetro de elección (modo de filtro,
 * forma de LFO, destino...): el nombre del parámetro encima y, debajo, una
 * etiqueta con la opción actual que abre el menú con todas. Las opciones son las
 * [SamplerParam.labels] del propio parámetro, así que no se repiten en la UI.
 */
@Composable
fun SamplerChoiceChip(
    param: SamplerParam,
    state: SamplerFxState,
    controller: SamplerFxController,
    modifier: Modifier = Modifier
) {
    val options = param.labels ?: return
    var open by remember { mutableStateOf(false) }
    val selected = param.read(state).roundToInt().coerceIn(0, options.size - 1)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = param.label.uppercase(),
            color = MixerCaptionText,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        FxChip(
            text = "${options[selected]} \u25BE",
            color = NeonCyan,
            contentDescription = "${param.label}: ${options[selected]}",
            onClick = { open = true }
        )
        FxMenu(expanded = open, onDismiss = { open = false }) {
            options.forEachIndexed { index, label ->
                FxMenuItem(
                    text = label,
                    color = if (index == selected) NeonCyan else TextDim,
                    onClick = {
                        open = false
                        controller.onParamChange(param, index.toFloat())
                    }
                )
            }
        }
    }
}
