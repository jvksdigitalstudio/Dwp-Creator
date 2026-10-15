package com.jvk.dwpcreator.sampler.api.state

import com.jvk.dwpcreator.sampler.api.rhythm.LfoSync

/** Un LFO por voz. Con `amount = 0` (valor de fábrica) no modula nada. */
data class LfoState(
    val rateMode: LfoRateMode = LfoRateMode.HERTZ,
    val rateHz: Float = 5f,
    /** Índice en [LfoSync.NAMES] cuando [rateMode] es tempo. */
    val division: Int = LfoSync.DEFAULT_INDEX,
    val shape: LfoShape = LfoShape.SINE,
    /** Fase inicial 0..1 (de 0 a 360 grados) con la que arranca en cada nota. */
    val phase: Float = 0f,
    /** Tiempo que tarda el LFO en alcanzar su profundidad total tras cada nota. */
    val fadeMs: Float = 0f,
    /** Profundidad bipolar -1..1 (el signo invierte la onda). */
    val amount: Float = 0f,
    val destination: LfoDestination = LfoDestination.PITCH
) {
    companion object {
        val LFO_1 = LfoState(rateHz = 5f, destination = LfoDestination.PITCH)
        val LFO_2 = LfoState(rateHz = 0.5f, destination = LfoDestination.FILTER)
    }
}
