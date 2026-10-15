package com.jvk.dwpcreator.sampler.api.state

/**
 * Ajustes de reproducción de la muestra de cada voz: afinación, punto de
 * inicio, bucle, dirección, sensibilidad a la velocidad y modo de ejecución
 * (legato / glide). El encendido mono/poly vive en el bus
 * ([com.jvk.dwpcreator.sampler.api.state.SamplerFxState.monophonic]).
 *
 * Los valores por defecto reproducen la muestra tal cual (sin transposición,
 * desde el principio, velocidad lineal), de modo que con el estado de fábrica
 * el sonido no cambia.
 */
data class SampleState(
    /** Transposición en semitonos. */
    val pitchSemitones: Float = 0f,
    /** Afinación fina en centésimas de semitono. */
    val fineCents: Float = 0f,
    /** Punto de inicio dentro de la muestra, 0..1 de su duración. */
    val startFraction: Float = 0f,
    /** Seguimiento de teclado de la afinación: 1 = una nota por semitono respecto a la tecla raíz. */
    val pitchTrack: Float = 1f,
    /** 0 = la velocidad no afecta al nivel; 1 = nivel proporcional a la velocidad. */
    val velocitySensitivity: Float = 1f,
    /** Rango de la rueda de pitch bend, en semitonos. */
    val bendRangeSemitones: Float = 2f,
    /** Tiempo de glide entre notas; 0 = sin glide. */
    val glideMs: Float = 0f,
    val loopMode: LoopMode = LoopMode.FORWARD,
    /** Reproduce la muestra al revés. Sin efecto si el bucle está activo y la muestra tiene puntos de bucle. */
    val reverse: Boolean = false,
    /** Con mono activo: una nota que solapa a otra la continúa en la misma voz sin reiniciar envolventes. */
    val legato: Boolean = false,
    /** `true` = el glide sólo une notas solapadas; `false` = toda nota nueva se desliza desde la anterior. */
    val glideOnlyLegato: Boolean = true
)
