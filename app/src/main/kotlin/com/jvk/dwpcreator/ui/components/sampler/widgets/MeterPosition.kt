package com.jvk.dwpcreator.ui.components.sampler.widgets

import kotlin.math.log10

/** Pico lineal -> posición 0..1 en una escala de -60..0 dB. */
internal fun meterPosition(peak: Float): Float {
    if (peak <= 0f) return 0f
    val db = 20f * log10(peak)
    return ((db + 60f) / 60f).coerceIn(0f, 1f)
}
