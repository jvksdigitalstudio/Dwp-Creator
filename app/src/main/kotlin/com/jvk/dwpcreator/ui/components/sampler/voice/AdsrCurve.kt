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
import com.jvk.dwpcreator.sampler.api.state.AdsrState
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.TextDim
import kotlin.math.abs
import kotlin.math.max

/**
 * Curva de una envolvente ADSR (ver [AdsrCurveGeometry]). [amount] es la
 * cantidad bipolar de las envolventes de modulación: su valor absoluto escala la
 * altura y un valor negativo dibuja la curva hacia abajo desde la línea superior.
 * Con la cantidad en 0 se dibuja una curva mínima y atenuada: la envolvente no
 * está modulando nada.
 */
@Composable
fun AdsrCurve(
    adsr: AdsrState,
    modifier: Modifier = Modifier,
    amount: Float = 1f,
    enabled: Boolean = true
) {
    Canvas(modifier = modifier.width(PreviewSize.WIDTH).height(56.dp)) {
        val points = AdsrCurveGeometry.points(adsr)
        val inset = 4.dp.toPx()
        val width = size.width - 2f * inset
        val height = size.height - 2f * inset
        val negative = amount < 0f
        val scale = max(abs(amount), MIN_VISIBLE_AMOUNT)
        val baseline = if (negative) inset else inset + height

        fun toOffset(p: CurvePoint) = Offset(
            x = inset + p.x * width,
            y = if (negative) baseline + p.y * height * scale else baseline - p.y * height * scale
        )

        drawLine(MixerControlBorder, Offset(inset, baseline), Offset(inset + width, baseline), strokeWidth = 1.dp.toPx())
        val line = Path().apply {
            val first = toOffset(points.first())
            moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val o = toOffset(points[i])
                lineTo(o.x, o.y)
            }
        }
        val color = if (enabled && amount != 0f) NeonCyan else TextDim.copy(alpha = 0.6f)
        drawPath(line, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}
