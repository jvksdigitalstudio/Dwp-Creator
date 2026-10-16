package com.jvk.dwpcreator.sampler.api.params

/**
 * Unidad con la que se muestra un parámetro. La conversión valor -> texto vive
 * en un único sitio: [ParamFormatter].
 */
enum class FxUnit {
    PERCENT,
    DECIBEL,
    HERTZ,
    MILLISECONDS,
    SECONDS,
    BPM,

    /** Semitonos con signo. */
    SEMITONES,

    /** Centésimas de semitono con signo. */
    CENTS,

    /** Corte de filtro normalizado 0..1 mostrado como frecuencia. */
    CUTOFF,

    /** Fase 0..1 mostrada en grados. */
    DEGREES,

    /** Parámetro de elección: el valor es el índice de una lista de etiquetas. */
    CHOICE
}
