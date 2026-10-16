package com.jvk.dwpcreator.ui.components.sampler.voice

import com.jvk.dwpcreator.sampler.api.state.AdsrState
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Geometría de la curva ADSR que dibuja la UI. Función pura (sin Compose):
 * sigue la misma ley que el motor ([com.jvk.dwpcreator.sampler.engine.voice.Adsr]) --
 * ataque lineal, decaimiento y release exponenciales de 60 dB -- para que lo que
 * se ve sea lo que suena.
 *
 * El eje de tiempos se **comprime con raíz cuadrada** (un release de 10 s no
 * aplasta a un ataque de 3 ms) y el sostenido se muestra con un tramo de
 * anchura fija proporcional.
 */
object AdsrCurveGeometry {

    private const val HOLD_FRACTION = 0.25f
    private const val SETTLED_SUSTAIN = 0.999f

    /** `y` va de 0 (reposo) a 1 (pico). El primer punto es `(0, 0)` y el último `(1, 0)`. */
    fun points(adsr: AdsrState, segmentSamples: Int = 24): List<CurvePoint> {
        val sustain = adsr.sustain.coerceIn(0f, 1f)
        val attackWidth = sqrt(adsr.attackMs)
        val decayWidth = if (sustain >= SETTLED_SUSTAIN) 0f else sqrt(adsr.decayMs)
        val releaseWidth = sqrt(adsr.releaseMs)
        val holdWidth = HOLD_FRACTION * (attackWidth + decayWidth + releaseWidth)
        val total = attackWidth + decayWidth + holdWidth + releaseWidth

        val out = ArrayList<CurvePoint>(segmentSamples * 2 + 4)
        var x = 0f
        out += CurvePoint(0f, 0f)

        x += attackWidth / total
        out += CurvePoint(x, 1f)

        if (decayWidth > 0f) {
            val start = x
            for (k in 1..segmentSamples) {
                val t = k / segmentSamples.toFloat()
                val y = if (k == segmentSamples) sustain else sustain + (1f - sustain) * DROP_60_DB.pow(t)
                out += CurvePoint(start + decayWidth / total * t, y)
            }
            x += decayWidth / total
        }

        x += holdWidth / total
        out += CurvePoint(x, sustain)

        val releaseStart = x
        for (k in 1..segmentSamples) {
            val t = k / segmentSamples.toFloat()
            val y = if (k == segmentSamples) 0f else sustain * DROP_60_DB.pow(t)
            out += CurvePoint(releaseStart + releaseWidth / total * t, y)
        }
        // El último `x` acumulado puede quedar a 1e-7 de 1: se fija el cierre exacto.
        out[out.lastIndex] = CurvePoint(1f, 0f)
        return out
    }

    /** Factor de caída de 60 dB (0,001) sobre un tramo completo. */
    private const val DROP_60_DB = 0.001f
}
