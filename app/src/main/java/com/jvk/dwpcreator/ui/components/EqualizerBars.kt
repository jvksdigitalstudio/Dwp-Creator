package com.jvk.dwpcreator.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.NeonGreen
import com.jvk.dwpcreator.ui.theme.NeonPurple

/**
 * La animación de ecualizador premium, compartida por [ImportingOverlay] y
 * [ExportProgressOverlay] -- extraída a un único sitio para que ambos
 * overlays de "operación en curso" se vean y se muevan exactamente igual,
 * sin duplicar la lógica de animación (ni sus posibles correcciones
 * futuras) en dos archivos.
 */

/**
 * Easing "standard" de Material Design 3 (ease-in-out suave, sin tramos
 * lineales) -- la misma familia de curva que usan los indicadores de
 * progreso y transiciones nativas de Android. Con `LinearEasing` cada barra
 * rebota a velocidad constante entre su mínimo y máximo (arranca y frena en
 * seco en cada extremo), lo que se percibe como un tirón mecánico, no como
 * una animación fluida.
 */
internal val EqualizerEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** Una barra del ecualizador: color, rango de altura y desfase respecto a las demás. */
private data class BarSpec(
    val color: Color,
    val minHeight: Float,
    val maxHeight: Float,
    val periodMs: Int,
    val delayMs: Int
)

@Composable
internal fun EqualizerBars() {
    // Un ecualizador real (Spotify, Apple Music, cualquier DAW) desfasa
    // cada barra unos ~110ms respecto a la anterior, para que el movimiento
    // se lea como una ola de izquierda a derecha, no como un flash
    // sincronizado. Las alturas también varían por barra (más altas al
    // centro) en vez de ser todas idénticas, que es como se ve un
    // ecualizador real.
    val bars = listOf(
        BarSpec(NeonPurple, minHeight = 10f, maxHeight = 26f, periodMs = 900, delayMs = 0),
        BarSpec(NeonCyan, minHeight = 13f, maxHeight = 34f, periodMs = 760, delayMs = 110),
        BarSpec(NeonGreen, minHeight = 15f, maxHeight = 40f, periodMs = 680, delayMs = 220),
        BarSpec(NeonCyan, minHeight = 13f, maxHeight = 34f, periodMs = 760, delayMs = 330),
        BarSpec(NeonPurple, minHeight = 10f, maxHeight = 26f, periodMs = 900, delayMs = 440)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom // barras "crecen" desde una base común, como un ecualizador real
    ) {
        bars.forEachIndexed { i, spec ->
            val transition = rememberInfiniteTransition(label = "eq$i")
            val height by transition.animateFloat(
                initialValue = spec.minHeight,
                targetValue = spec.maxHeight,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = spec.periodMs, easing = EqualizerEase),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(spec.delayMs, StartOffsetType.FastForward)
                ),
                label = "eqHeight$i"
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(spec.color)
            )
        }
    }
}
