package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.jvk.dwpcreator.ui.theme.MixerThumbHighlight
import kotlin.math.roundToInt

/** Opacidad del halo del "thumb" de Pan/Volumen -- constante (no depende de un estado presionado/reposo como Mute/Solo, solo de si se está arrastrando, ver [MixerThumb]). */
private const val MixerThumbGlowAlpha = 0.55f

/**
 * El "thumb" arrastrable compartido por Pan y Volumen: un pequeño disco con
 * relieve (degradado radial claro -> color) y un halo propio ([softGlow] --
 * ver su KDoc para el porqué de un halo dibujado a mano en vez de una
 * sombra real) en vez de un círculo plano de un solo color, que además
 * crece ([scale]) mientras el dedo lo arrastra -- la retroalimentación
 * táctil de "lo tengo agarrado" de cualquier control profesional. Siempre
 * anclado a [Alignment.CenterStart] de su [BoxScope] contenedor (Pan y
 * Volumen miden la posición desde el borde izquierdo del riel) y
 * desplazado por [offsetPx].
 */
@Composable
internal fun BoxScope.MixerThumb(offsetPx: Float, scale: Float, glowColor: Color) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(offsetPx.roundToInt(), 0) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(MixerThumbDiameter)
            .softGlow(color = glowColor, alpha = MixerThumbGlowAlpha)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(MixerThumbHighlight, glowColor),
                    radius = with(density) { MixerThumbDiameter.toPx() }
                )
            )
    )
}
