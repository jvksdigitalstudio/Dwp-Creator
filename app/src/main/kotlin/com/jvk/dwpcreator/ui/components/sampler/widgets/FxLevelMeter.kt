package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerMuteActive
import com.jvk.dwpcreator.ui.theme.MixerSoloActive
import com.jvk.dwpcreator.ui.theme.NeonGreen

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

private fun meterColor(position: Float): Color = when {
    position >= 0.99f -> MixerMuteActive
    position >= 0.9f -> MixerSoloActive
    else -> NeonGreen
}
