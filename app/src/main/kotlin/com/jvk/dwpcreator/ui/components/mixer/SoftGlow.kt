package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Cuánto más grande que el propio control es el halo dibujado -- lo bastante para leerse como "luz alrededor", sin invadir controles vecinos en una franja tan compacta. */
private const val GlowExtraRadiusFraction = 0.65f

/**
 * Sustituto de una sombra dinámica real ([androidx.compose.ui.draw.shadow])
 * para el brillo "premium" de Mute/Solo y de los "thumbs" de Pan/Volumen.
 *
 * **Por qué existe esta función -- motivo real de rendimiento, no un
 * capricho estilístico.** El diseño original (`Doc/42`) usaba
 * `Modifier.shadow(elevation, CircleShape, clip = false, ambientColor =
 * ..., spotColor = ...)` en estos cuatro controles. Una sombra de Compose
 * con `clip = false` + `ambientColor`/`spotColor` PROPIOS (no el negro por
 * defecto) obliga a Android a componer esa sombra en una capa aparte,
 * calculada por software -- mucho más cara que la sombra de elevación
 * nativa barata que usa la plataforma normalmente. Esta franja monta CUATRO
 * de esos controles por fila (Mute, Solo, thumb de Pan, thumb de Volumen);
 * en una lista de 48 filas, con ~12-14 filas visibles/pre-cargadas a la vez
 * durante un scroll rápido (`LazyColumn`), eso son decenas de esas capas
 * recalculándose en cada frame de scroll -- la causa real y reproducible
 * del lag/retardo reportado por el usuario al deslizar el dedo por la
 * lista, no un problema difuso de "rendimiento en general".
 *
 * La corrección real (bajar el costo, no la calidad visual) es sustituir
 * esa sombra dinámica por un halo dibujado A MANO con
 * [androidx.compose.ui.draw.drawBehind]: un degradado radial que se
 * desvanece hacia fuera, pintado directamente en el mismo `DrawScope` del
 * control -- una operación de dibujo pura, sin capa de composición aparte,
 * sin que Android tenga que recalcular nada propio en cada frame. El
 * resultado percibido (un halo de color alrededor del control) es
 * prácticamente el mismo; el costo real de renderizado es una fracción del
 * de una sombra dinámica.
 */
internal fun Modifier.softGlow(
    color: Color,
    alpha: Float,
    extraRadiusFraction: Float = GlowExtraRadiusFraction
): Modifier = this.drawBehind {
    if (alpha <= 0f) return@drawBehind
    val glowRadius = (size.minDimension / 2f) * (1f + extraRadiusFraction)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            radius = glowRadius,
            center = center
        ),
        radius = glowRadius,
        center = center
    )
}
