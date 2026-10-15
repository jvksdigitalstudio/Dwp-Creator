package com.jvk.dwpcreator.ui.components.sampler.voice

/**
 * Escala mínima (fracción de la altura) con la que se dibujan las curvas de
 * previsualización de modulación ([AdsrCurve], [LfoWavePreview]) cuando la
 * cantidad es 0 o casi 0: así la curva sigue siendo visible, atenuada, en vez
 * de colapsar en una línea plana.
 */
internal const val MIN_VISIBLE_AMOUNT = 0.12f
