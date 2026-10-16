package com.jvk.dwpcreator.ui.components.sampler.voice

import com.jvk.dwpcreator.sampler.api.curve.LfoWaveforms
import com.jvk.dwpcreator.sampler.api.state.LfoShape
import kotlin.math.floor

/**
 * Geometría de la previsualización del LFO. Función pura que usa las mismas
 * formas de onda que el oscilador de audio ([LfoWaveforms]); las formas
 * aleatorias se dibujan con una secuencia fija para que la imagen sea estable.
 */
object LfoPreviewGeometry {

    private val RANDOM_SEQUENCE = floatArrayOf(0.7f, -0.5f, 0.9f, -0.8f, 0.3f, -0.2f)

    /**
     * [cycles] ciclos de [shape] empezando en la fase [startPhase] (0..1). `y` es
     * bipolar (-1..1) y `x` va de 0 a 1.
     */
    fun points(shape: LfoShape, startPhase: Float, cycles: Int = 2, samplesPerCycle: Int = 64): List<CurvePoint> {
        val total = cycles * samplesPerCycle
        val out = ArrayList<CurvePoint>(total + 1)
        for (i in 0..total) {
            val position = startPhase.coerceIn(0f, 1f) + i.toFloat() / samplesPerCycle
            val cycle = floor(position).toInt()
            val phase = position - cycle
            val held = RANDOM_SEQUENCE[cycle % RANDOM_SEQUENCE.size]
            val next = RANDOM_SEQUENCE[(cycle + 1) % RANDOM_SEQUENCE.size]
            out += CurvePoint(i.toFloat() / total, LfoWaveforms.value(shape, phase, held, next))
        }
        return out
    }
}
