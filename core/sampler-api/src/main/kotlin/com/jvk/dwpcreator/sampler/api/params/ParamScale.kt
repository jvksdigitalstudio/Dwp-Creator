package com.jvk.dwpcreator.sampler.api.params

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Escala numérica de un parámetro: rango, valor de fábrica, curva del knob y
 * rejilla opcional. Es la **única** implementación de la conversión entre el
 * valor real (dB, Hz, ms, índice...) y la posición normalizada 0..1 de un
 * control; la usan todos los parámetros ([SamplerParam]).
 *
 * @param steps `0` = continuo; `n > 0` = rejilla de `n` intervalos (`n + 1`
 *   posiciones): semitonos enteros, índices de una lista de opciones...
 */
class ParamScale(
    val min: Float,
    val max: Float,
    val default: Float,
    val curve: FxCurve = FxCurve.LINEAR,
    val steps: Int = 0
) {
    init {
        require(max > min) { "max ($max) debe ser mayor que min ($min)" }
        require(curve != FxCurve.LOG || min > 0f) { "una escala logarítmica exige min > 0 (min = $min)" }
        require(default in min..max) { "default ($default) fuera de [$min, $max]" }
    }

    /** Satura [value] al rango y, si la escala es escalonada, lo ajusta a la rejilla. */
    fun clamp(value: Float): Float = snap(value.coerceIn(min, max))

    /** Ajusta [value] a la rejilla de [steps]; una escala continua lo devuelve intacto. */
    fun snap(value: Float): Float {
        if (steps <= 0) return value
        val stepSize = (max - min) / steps
        return min + ((value - min) / stepSize).roundToInt() * stepSize
    }

    /** Posición 0..1 del knob para [value]. */
    fun toNormalized(value: Float): Float {
        val v = value.coerceIn(min, max)
        val n = when (curve) {
            FxCurve.LINEAR -> (v - min) / (max - min)
            FxCurve.LOG -> ln(v / min) / ln(max / min)
            FxCurve.SQUARED -> sqrt((v - min) / (max - min))
        }
        return n.coerceIn(0f, 1f)
    }

    /** Valor real (ya ajustado a la rejilla) para la posición [normalized] del knob. */
    fun fromNormalized(normalized: Float): Float {
        val n = normalized.coerceIn(0f, 1f)
        return snap(
            when (curve) {
                FxCurve.LINEAR -> min + n * (max - min)
                FxCurve.LOG -> min * (max / min).pow(n)
                FxCurve.SQUARED -> min + n * n * (max - min)
            }
        )
    }
}
