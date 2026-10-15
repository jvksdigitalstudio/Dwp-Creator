package com.jvk.dwpcreator.sampler.api.state

/**
 * Filtro por voz. Con el estado de fábrica (pasa-bajos totalmente abierto,
 * sin resonancia ni modulación) el filtro se **puentea** y la señal no se toca.
 */
data class FilterState(
    val enabled: Boolean = true,
    val mode: FilterMode = FilterMode.LOWPASS_12,
    /** Corte normalizado 0..1 (20 Hz..~20 kHz, escala logarítmica; ver [FilterScaling]). */
    val cutoff: Float = 1f,
    val resonance: Float = 0f,
    /** Saturación previa al filtro. */
    val drive: Float = 0f,
    /** Seguimiento de teclado del corte, bipolar: 1 = el corte sube una octava por octava de la nota. */
    val keyTrack: Float = 0f,
    /** Sensibilidad del corte a la velocidad, bipolar. */
    val velocityTrack: Float = 0f
)
