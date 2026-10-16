package com.jvk.dwpcreator.ui.components.common

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Easing "standard" de Material Design 3 (ease-in-out suave, sin tramos
 * lineales) -- la misma familia de curva que usan los indicadores de
 * progreso y transiciones nativas de Android. Con `LinearEasing` cada barra
 * rebota a velocidad constante entre su mínimo y máximo (arranca y frena en
 * seco en cada extremo), lo que se percibe como un tirón mecánico, no como
 * una animación fluida.
 */
internal val EqualizerEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
