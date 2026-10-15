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
import com.jvk.dwpcreator.sampler.api.curve.FilterResponse
import com.jvk.dwpcreator.sampler.api.math.FilterScaling
import com.jvk.dwpcreator.sampler.api.state.FilterState
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.TextDim

/**
 * Respuesta en frecuencia del filtro de voz, dibujada con la misma función que
 * usan las pruebas ([FilterResponse]): 20 Hz..20 kHz en escala logarítmica y
 * [MIN_DB]..[MAX_DB] dB. Una línea vertical marca el corte base.
 */
@Composable
fun FilterResponseCurve(filter: FilterState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(PreviewSize.WIDTH).height(72.dp)) {
        val inset = 4.dp.toPx()
        val width = size.width - 2f * inset
        val height = size.height - 2f * inset
        val octaves = FilterScaling.OCTAVES
        val cutoffHz = FilterScaling.cutoffHz(filter.cutoff)

        fun xOf(octavesAbove: Float) = inset + width * (octavesAbove / octaves)
        fun yOf(db: Float) = inset + height * (1f - (db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)

        drawLine(MixerControlBorder, Offset(inset, yOf(0f)), Offset(inset + width, yOf(0f)), strokeWidth = 1.dp.toPx())
        drawLine(
            NeonPurple.copy(alpha = 0.7f),
            Offset(xOf(filter.cutoff * octaves), inset),
            Offset(xOf(filter.cutoff * octaves), inset + height),
            strokeWidth = 1.dp.toPx()
        )

        val curve = Path()
        for (i in 0..POINTS) {
            val octavesAbove = octaves * i / POINTS
            val hz = FilterScaling.hzFromOctaves(octavesAbove)
            val db = FilterResponse.magnitudeDb(filter.mode, cutoffHz, filter.resonance, hz)
            if (i == 0) curve.moveTo(xOf(octavesAbove), yOf(db)) else curve.lineTo(xOf(octavesAbove), yOf(db))
        }
        val color = if (filter.enabled) NeonCyan else TextDim.copy(alpha = 0.6f)
        drawPath(curve, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}

private const val POINTS = 120

private const val MIN_DB = -36f

private const val MAX_DB = 24f
