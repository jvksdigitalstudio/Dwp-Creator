package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerControlFill
import com.jvk.dwpcreator.ui.theme.MixerEndLabelText

/**
 * Riel horizontal con un "thumb" arrastrable que representa el paneo
 * (-1f..1f, centrado en 0f) -- arrastrar en cualquier punto del riel mueve
 * el thumb a esa posición X (no es un `delta` relativo: se salta
 * directamente a donde se toca/arrastra, más intuitivo en un control tan
 * pequeño). La marca vertical del centro es una referencia visual fija de
 * "pan = 0"; "L"/"R" al fondo del riel son una referencia de extremos, no
 * texto interactivo.
 */
@Composable
internal fun MixerPanControl(pan: Float, onPanChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    // `pointerInput(Unit)` no se reinicia nunca: sin esto capturaría el PRIMER
    // `onPanChange` recibido y seguiría llamándolo aunque la fila recomponga con
    // otro (mismo patrón que ya usa `PianoKeyGestureOverlay` en MainScreen).
    val currentOnPanChange by rememberUpdatedState(onPanChange)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbSizePx = with(density) { MixerThumbDiameter.toPx() }
    val fraction = ((pan.coerceIn(-1f, 1f) + 1f) / 2f)
    val maxOffsetPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)
    val thumbScale by animateFloatAsState(if (dragging) DraggingThumbScale else 1f, label = "panThumbScale")

    MixerTrackSurface(
        modifier = modifier
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapAndHorizontalDrag(
                    onDraggingChange = { dragging = it },
                    onPositionChange = { xPx, widthPx -> currentOnPanChange(xToPan(xPx, widthPx)) }
                )
            }
            .semantics { contentDescription = "Paneo de la muestra" }
    ) {
        Text("L", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
        Text("R", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp))
        // Marca fija del centro (pan = 0), de referencia visual.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(MixerControlBorder)
        )
        MixerThumb(offsetPx = fraction * maxOffsetPx, scale = thumbScale, glowColor = MixerControlFill)
    }
}
