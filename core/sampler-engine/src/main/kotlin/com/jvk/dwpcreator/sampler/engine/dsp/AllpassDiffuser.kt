package com.jvk.dwpcreator.sampler.engine.dsp

import com.jvk.dwpcreator.sampler.api.math.Dsp

/**
 * Difusor *all-pass* de Schroeder de longitud entera fija. Forma
 * canónica: `v[n] = x[n] + g·v[n-N]`, `y[n] = v[n-N] - g·v[n]`.
 * Magnitud de respuesta plana (todo-paso) para `|g| < 1`: espesa la densidad
 * de ecos de la reverb sin colorear el espectro.
 */
internal class AllpassDiffuser(lengthSamples: Int) {
    private val length = lengthSamples.coerceAtLeast(2)
    private val buffer = FloatArray(length)
    private var index = 0

    fun process(x: Float, g: Float): Float {
        val delayed = buffer[index]
        val v = Dsp.flush(x + g * delayed)
        buffer[index] = v
        index++
        if (index >= length) index = 0
        return delayed - g * v
    }

    fun clear() {
        buffer.fill(0f)
        index = 0
    }
}
