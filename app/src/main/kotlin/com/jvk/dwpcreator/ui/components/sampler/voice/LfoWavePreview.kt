package com.jvk.dwpcreator.ui.components.sampler.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.sampler.api.state.LfoState
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.TextDim
import kotlin.math.abs
import kotlin.math.max

/**
 * Forma de onda del LFO ([LfoPreviewGeometry]) escalada por su profundidad: el
 * signo de la cantidad invierte la onda y su valor absoluto la amplitud. Con
 * cantidad 0 se dibuja una onda mínima y atenuada (el LFO no modula nada).
 */
@Composable
fun LfoWavePreview(lfo: LfoState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(PreviewSize.WIDTH).height(48.dp)) {
        val inset = 4.dp.toPx()
        val width = size.width - 2f * inset
        val half = (size.height - 2f * inset) / 2f
        val centreY = inset + half
        val scale = max(abs(lfo.amount), MIN_VISIBLE_AMOUNT) * (if (lfo.amount < 0f) -1f else 1f)

        drawLine(MixerControlBorder, Offset(inset, centreY), Offset(inset + width, centreY), strokeWidth = 1.dp.toPx())
        val path = Path()
        LfoPreviewGeometry.points(lfo.shape, lfo.phase).forEachIndexed { i, p ->
            val x = inset + p.x * width
            val y = centreY - p.y * scale * half
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val color = if (lfo.amount != 0f) NeonCyan else TextDim.copy(alpha = 0.6f)
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}
