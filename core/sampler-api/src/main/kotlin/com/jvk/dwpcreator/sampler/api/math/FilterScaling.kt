package com.jvk.dwpcreator.sampler.api.math

import kotlin.math.pow

/**
 * Relación entre los controles normalizados del filtro y sus magnitudes físicas.
 * Único sitio donde se define: el motor, la curva de respuesta de la UI y el
 * formato del texto leen estas mismas funciones.
 *
 * - Corte: 0..1 recorre [OCTAVES] octavas desde [MIN_HZ] (20 Hz .. ~20,5 kHz).
 * - Resonancia: 0..1 recorre Q de [MIN_Q] (Butterworth, sin resonancia) a
 *   [MAX_Q], de forma exponencial.
 */
object FilterScaling {
    const val MIN_HZ = 20f
    const val OCTAVES = 10f
    const val MIN_Q = 0.7071f
    const val MAX_Q = 10f

    /** Octavas por encima de [MIN_HZ] para un corte normalizado. */
    fun cutoffOctaves(cutoff: Float): Float = cutoff.coerceIn(0f, 1f) * OCTAVES

    /** Frecuencia en Hz de [octaves] octavas por encima de [MIN_HZ]. */
    fun hzFromOctaves(octaves: Float): Float = MIN_HZ * 2f.pow(octaves)

    fun cutoffHz(cutoff: Float): Float = hzFromOctaves(cutoffOctaves(cutoff))

    fun q(resonance: Float): Float = MIN_Q * (MAX_Q / MIN_Q).pow(resonance.coerceIn(0f, 1f))

    /** Amortiguamiento `k = 1/Q` que usa el filtro de variable de estado. */
    fun damping(resonance: Float): Float = 1f / q(resonance)
}
