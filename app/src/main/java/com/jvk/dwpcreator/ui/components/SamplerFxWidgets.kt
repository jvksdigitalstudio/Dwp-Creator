package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.audio.dsp.FxModule
import com.jvk.dwpcreator.audio.dsp.FxParam
import com.jvk.dwpcreator.audio.dsp.FxPresets
import com.jvk.dwpcreator.audio.dsp.SamplerFxState
import com.jvk.dwpcreator.ui.state.SamplerFxController
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextDark
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerMuteActive
import com.jvk.dwpcreator.ui.theme.MixerSoloActive
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientBottom
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.NeonGreen
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt
import com.jvk.dwpcreator.ui.theme.SurfacePurple
import com.jvk.dwpcreator.ui.theme.TextDim
import kotlin.math.log10

/**
 * Tarjeta de un módulo del sampler (MASTER, FX DRIVE A, FX DELAY...): título,
 * interruptor de encendido opcional y el contenido del módulo.
 *
 * [powered] = `null` -> el módulo no se puede apagar (MASTER, sin LED).
 * [headerExtras] se dibuja a la derecha del LED (selector de presets).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FxModuleCard(
    title: String,
    powered: Boolean?,
    onPoweredChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    headerExtras: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfacePurple)
            .border(1.dp, MixerControlBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = if (powered == false) TextDim else NeonPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (powered != null) {
                // Zona táctil de 32 dp alrededor de un LED de 10 dp.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(role = Role.Switch) { onPoweredChange(!powered) }
                        .semantics { contentDescription = "$title encendido" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (powered) NeonGreen else MixerControlBorder)
                    )
                }
            }
            headerExtras()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

/** Selector de 2+ opciones excluyentes (POLY/MONO, NORMAL/BOUNCE). */
@Composable
fun FxSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MixerControlBorder, RoundedCornerShape(6.dp))
    ) {
        options.forEachIndexed { index, text ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .background(if (selected) NeonCyan else MixerTrackGradientBottom)
                    .clickable(role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (selected) ActiveAccentTextDark else TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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
    var open by remember { mutableStateOf(false) }
    val active = FxPresets.activeName(module, state)
    val isCustom = active == FxPresets.CUSTOM
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MixerControlBorder, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "Preset de ${module.title}: $active" }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = "$active \u25BE",
                color = if (isCustom) TextDim else NeonCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(SurfacePurpleAlt)
        ) {
            for (preset in FxPresets.forModule(module)) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = preset.name,
                            color = if (preset.name == active) NeonCyan else TextDim,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        open = false
                        controller.onPreset(module, preset)
                    }
                )
            }
            HorizontalDivider(color = MixerControlBorder)
            DropdownMenuItem(
                text = { Text(text = "Reset module", color = NeonPurple, fontSize = 13.sp) },
                onClick = {
                    open = false
                    controller.onModuleReset(module)
                }
            )
        }
    }
}

/** Knob ligado a un [FxParam]: lee el valor del [state], escribe vía [controller] y restaura el de fábrica con doble toque. */
@Composable
fun FxParamKnob(
    param: FxParam,
    state: SamplerFxState,
    controller: SamplerFxController,
    moduleEnabled: Boolean
) {
    val value = param.read(state)
    FxKnob(
        label = param.label,
        normalized = param.toNormalized(value),
        valueText = param.format(value),
        enabled = moduleEnabled,
        onNormalizedChange = { controller.onParamChange(param, param.fromNormalized(it)) },
        onReset = { controller.onParamReset(param) }
    )
}

/**
 * Vúmetro de pico estéreo (dos barras finas): verde en nivel normal, ámbar
 * cerca de 0 dBFS y rojo en el techo del limitador. Escala -60..0 dB.
 */
@Composable
fun FxLevelMeter(peakLeft: Float, peakRight: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 72.dp, height = 12.dp)) {
        val barHeight = 4.dp.toPx()
        val gap = 2.dp.toPx()
        val radius = CornerRadius(barHeight / 2f)
        val levels = floatArrayOf(meterPosition(peakLeft), meterPosition(peakRight))
        for (channel in 0..1) {
            val top = channel * (barHeight + gap)
            drawRoundRect(
                color = MixerControlBorder,
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = radius
            )
            val level = levels[channel]
            if (level > 0f) {
                drawRoundRect(
                    color = meterColor(level),
                    topLeft = Offset(0f, top),
                    size = Size(size.width * level, barHeight),
                    cornerRadius = radius
                )
            }
        }
    }
}

/** Pico lineal -> posición 0..1 en una escala de -60..0 dB. */
internal fun meterPosition(peak: Float): Float {
    if (peak <= 0f) return 0f
    val db = 20f * log10(peak)
    return ((db + 60f) / 60f).coerceIn(0f, 1f)
}

private fun meterColor(position: Float): Color = when {
    position >= 0.99f -> MixerMuteActive
    position >= 0.9f -> MixerSoloActive
    else -> NeonGreen
}
