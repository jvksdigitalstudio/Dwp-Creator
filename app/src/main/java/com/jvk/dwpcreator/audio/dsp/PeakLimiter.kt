package com.jvk.dwpcreator.audio.dsp

import kotlin.math.abs
import kotlin.math.max

/**
 * Limitador de picos del bus maestro: ninguna combinación de notas
 * simultáneas + drive + colas de efectos puede superar [ceiling] a la salida
 * (sin él, la suma de voces recortaría con distorsión dura en el HAL).
 *
 * Ataque instantáneo (la ganancia baja de golpe al valor que deja el pico
 * justo en el techo) y liberación exponencial de 120 ms, que es suave y
 * evita el "bombeo" audible. **Invariante:** la ganancia nunca excede la
 * máxima que respeta el techo para la muestra actual, así que
 * `|salida| <= ceiling` siempre. Con señal por debajo del techo la ganancia es
 * exactamente 1: el limitador es transparente y no altera el sonido normal.
 */
internal class PeakLimiter(sampleRate: Float, private val ceiling: Float = 0.97f) {

    private val releaseCoef = Dsp.smoothingCoef(120f, sampleRate)
    private var gain = 1f

    fun process(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val peak = max(abs(left[i]), abs(right[i]))
            val needed = if (peak > ceiling) ceiling / peak else 1f
            // La ganancia se relaja hacia 1 con la liberación, pero NUNCA por encima de
            // `needed` (la máxima que aún respeta el techo para ESTA muestra): sin ese tope,
            // con un pico que baja despacio el paso de liberación rebasaba `needed` y la
            // salida superaba el techo (hasta ~0.0018 con 32 voces).
            val relaxed = gain + (1f - gain) * releaseCoef
            gain = if (relaxed < needed) relaxed else needed
            left[i] *= gain
            right[i] *= gain
        }
    }
}
