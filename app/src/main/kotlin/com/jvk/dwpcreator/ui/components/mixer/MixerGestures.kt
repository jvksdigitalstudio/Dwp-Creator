package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * Gesto compartido de [MixerPanControl] y [MixerVolumeControl]: reacciona ya
 * en el primer toque (antes solo `detectHorizontalDragGestures`, que exige
 * superar el umbral de movimiento del sistema para siquiera empezar -- un
 * toque suelto y corto, sin arrastrar, no hacía nada) y sigue el dedo
 * mientras se mantenga presionado, igual que un fader real.
 */
internal suspend fun PointerInputScope.detectTapAndHorizontalDrag(
    onDraggingChange: (Boolean) -> Unit,
    onPositionChange: (xPx: Float, widthPx: Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val widthPx = size.width.toFloat()
        onDraggingChange(true)
        if (widthPx > 0f) onPositionChange(down.position.x, widthPx)
        val pointerId = down.id
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) break
            if (widthPx > 0f) onPositionChange(change.position.x, widthPx)
            change.consume()
        }
        onDraggingChange(false)
    }
}
