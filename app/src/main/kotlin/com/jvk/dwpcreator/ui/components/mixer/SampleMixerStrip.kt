package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Headset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.state.SampleMixerState
import com.jvk.dwpcreator.ui.theme.MixerMuteActive
import com.jvk.dwpcreator.ui.theme.MixerSoloActive

/**
 * Franja de mini-controles de mezcla de una muestra -- Mute, Solo, Pan y
 * Volumen -- junto al **nombre** de cada muestra (lado izquierdo de la fila,
 * lejos de la tecla de piano -- corrección explícita tras feedback: la
 * primera versión la colocaba pegada a la tecla, a la derecha, y el usuario
 * pidió moverla al lado del nombre). Ver el KDoc de [SampleMixerState] para
 * el alcance exacto (solo afecta la previsualización en vivo, no se exporta
 * al `.dwp`).
 *
 * **Lenguaje visual: el mismo que las teclas del piano, no uno inventado
 * aparte.** Cada pieza (botón Mute/Solo, riel de Pan/Volumen, "thumb"
 * arrastrable) usa la misma receta que
 * [PianoKeyBadge][com.jvk.dwpcreator.ui.components.piano.PianoKeyBadge]: un
 * degradado vertical de superficie (nunca un relleno plano) más una veta de
 * brillo horizontal cerca del borde superior que simula luz rebotando en un
 * canto físico, y el color de "activo" se deriva del tono base con `lerp`
 * (más claro arriba, más oscuro abajo) en vez de ser un color fijo aparte.
 * Mute/Solo, además, imitan un botón físico de consola: **elevados** (con
 * sombra) en reposo y **hundidos** (sin sombra, superficie más oscura) al
 * activarse -- así se "sienten" pulsados, no solo "coloreados". Usan iconos
 * reales (`VolumeOff`/`VolumeUp` para Mute, `Headset` para Solo -- el icono
 * estándar de "escuchar en solitario" en cualquier DAW) en vez de una letra
 * suelta. El "thumb" de Pan/Volumen brilla y crece ligeramente mientras se
 * arrastra, como retroalimentación táctil de que el dedo lo tiene agarrado.
 */
@Composable
fun SampleMixerStrip(
    state: SampleMixerState,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onPanChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MixerControlGap)
    ) {
        MixerToggleButton(
            icon = if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            active = state.muted,
            activeColor = MixerMuteActive,
            contentDescription = if (state.muted) "Quitar silencio a la muestra" else "Silenciar muestra",
            onClick = onToggleMute
        )
        MixerToggleButton(
            icon = Icons.Filled.Headset,
            active = state.solo,
            activeColor = MixerSoloActive,
            contentDescription = if (state.solo) "Quitar solo a la muestra" else "Poner la muestra en solo",
            onClick = onToggleSolo
        )
        MixerPanControl(
            pan = state.pan,
            onPanChange = onPanChange,
            modifier = Modifier.width(MixerPanWidth)
        )
        MixerVolumeControl(
            volume = state.volume,
            onVolumeChange = onVolumeChange,
            modifier = Modifier.width(MixerVolumeWidth)
        )
    }
}

private val MixerPanWidth = 42.dp

private val MixerVolumeWidth = 50.dp

private val MixerControlGap = 6.dp
