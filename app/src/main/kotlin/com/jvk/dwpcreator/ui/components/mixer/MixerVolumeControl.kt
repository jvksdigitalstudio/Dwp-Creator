package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.MixerControlFill
import com.jvk.dwpcreator.ui.theme.MixerEndLabelText

/**
 * Riel horizontal con un "thumb" arrastrable y un relleno progresivo con
 * degradado (estilo fader de consola real, no una barra sólida) que
 * representa el volumen (0f..1f). Igual que [MixerPanControl], arrastrar
 * salta directamente a la posición X tocada.
 */
@Composable
internal fun MixerVolumeControl(volume: Float, onVolumeChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    // Ver el comentario equivalente en [MixerPanControl].
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbSizePx = with(density) { MixerThumbDiameter.toPx() }
    val fraction = volume.coerceIn(0f, 1f)
    val maxOffsetPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)
    val thumbScale by animateFloatAsState(if (dragging) DraggingThumbScale else 1f, label = "volumeThumbScale")

    MixerTrackSurface(
        modifier = modifier
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapAndHorizontalDrag(
                    onDraggingChange = { dragging = it },
                    onPositionChange = { xPx, widthPx -> currentOnVolumeChange(xToFraction(xPx, widthPx)) }
                )
            }
            .semantics { contentDescription = "Volumen de la muestra" }
    ) {
        Text("−", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
        Text("+", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp))
        // Relleno tipo fader: degradado, no un color sólido -- se nota más
        // "cargado" hacia el extremo derecho a medida que sube el volumen,
        // igual que un fader iluminado de una consola real.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(lerp(MixerControlFill, Color.Black, 0.35f), MixerControlFill)
                    )
                )
        )
        MixerThumb(offsetPx = fraction * maxOffsetPx, scale = thumbScale, glowColor = MixerControlFill)
    }
}
