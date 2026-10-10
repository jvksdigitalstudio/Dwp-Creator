package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.ui.theme.MixerCaptionText
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientTop
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.TextDim
import kotlin.math.cos
import kotlin.math.sin

private val KnobSize = 52.dp
private val KnobColumnWidth = 64.dp

/** Arrastre (en dp) necesario para recorrer el knob entero: ni tan corto que sea brusco ni tan largo que canse. */
private val KnobFullRangeDragDp = 180.dp

private const val ArcStartAngle = 135f
private const val ArcSweepAngle = 270f

/**
 * Knob rotatorio al estilo DirectWave: arco de valor, cuerpo con
 * indicador, nombre encima y valor numérico debajo.
 *
 * - **Arrastrar** (arriba/derecha sube, abajo/izquierda baja) cambia el valor
 *   de forma relativa al punto de partida -- sin saltos al tocar.
 * - **Doble toque** restaura el valor de fábrica ([onReset]), el gesto
 *   estándar de los plugins de audio.
 * - **Accesibilidad**: expone el valor como control de progreso con acción
 *   `setProgress`, de modo que TalkBack/switch access pueden ajustarlo sin
 *   gestos de arrastre.
 *
 * Trabaja en valores **normalizados** 0..1; la conversión a la unidad real
 * (dB, Hz, ms...) la hace quien lo usa, con `FxParam`.
 *
 * [enabled] sólo cambia el color (el módulo está apagado): el knob sigue
 * siendo ajustable, como en DirectWave.
 */
@Composable
fun FxKnob(
    label: String,
    normalized: Float,
    valueText: String,
    enabled: Boolean,
    onNormalizedChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Los gestos viven en un `pointerInput(Unit)` que no se reinicia al
    // recomponer; estas referencias les dan siempre el valor/lambda vigente.
    val currentNormalized by rememberUpdatedState(normalized)
    val currentOnChange by rememberUpdatedState(onNormalizedChange)
    val currentOnReset by rememberUpdatedState(onReset)

    val activeColor = if (enabled) NeonCyan else TextDim.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .width(KnobColumnWidth)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(normalized, 0f..1f)
                setProgress { target ->
                    currentOnChange(target.coerceIn(0f, 1f))
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = MixerCaptionText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Canvas(
            modifier = Modifier
                .size(KnobSize)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { currentOnReset() })
                }
                .pointerInput(Unit) {
                    val fullRangePx = KnobFullRangeDragDp.toPx()
                    var accumulated = 0f
                    detectDragGestures(
                        onDragStart = { accumulated = currentNormalized },
                        onDrag = { change, drag ->
                            change.consume()
                            accumulated = (accumulated + (drag.x - drag.y) / fullRangePx).coerceIn(0f, 1f)
                            currentOnChange(accumulated)
                        }
                    )
                }
        ) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f + 1.dp.toPx()
            val arcSize = Size(size.width - 2f * inset, size.height - 2f * inset)
            val arcTopLeft = Offset(inset, inset)

            drawArc(
                color = MixerControlBorder,
                startAngle = ArcStartAngle,
                sweepAngle = ArcSweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (normalized > 0f) {
                drawArc(
                    color = activeColor,
                    startAngle = ArcStartAngle,
                    sweepAngle = ArcSweepAngle * normalized,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            val bodyRadius = size.minDimension / 2f - inset - stroke - 1.dp.toPx()
            drawCircle(color = MixerTrackGradientTop, radius = bodyRadius, center = center)

            val angle = Math.toRadians((ArcStartAngle + ArcSweepAngle * normalized).toDouble())
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                color = activeColor,
                start = center + direction * (bodyRadius * 0.35f),
                end = center + direction * (bodyRadius * 0.92f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        Text(
            text = valueText,
            color = TextDim,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
