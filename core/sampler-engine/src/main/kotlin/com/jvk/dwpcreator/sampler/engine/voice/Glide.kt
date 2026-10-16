package com.jvk.dwpcreator.sampler.engine.voice

import kotlin.math.abs
import kotlin.math.max

/**
 * Deslizamiento (portamento) de afinación de una voz. Mantiene un
 * desplazamiento en semitonos respecto a la afinación de destino que se reduce
 * **linealmente** hasta 0 en el tiempo pedido: la nota "viaja" desde la
 * anterior a velocidad constante en semitonos por segundo.
 */
internal class Glide {
    /** Semitonos que faltan para llegar a la afinación de destino. */
    var offsetSemitones: Float = 0f
        private set

    private var stepPerFrame = 0f

    fun reset() {
        offsetSemitones = 0f
        stepPerFrame = 0f
    }

    /** Parte de [startOffset] semitonos de distancia y la recorre en [frames] muestras. `frames <= 0` salta directamente. */
    fun begin(startOffset: Float, frames: Float) {
        if (frames <= 0f || startOffset == 0f) {
            reset()
            return
        }
        offsetSemitones = startOffset
        stepPerFrame = abs(startOffset) / max(1f, frames)
    }

    fun advance(frames: Int) {
        if (offsetSemitones == 0f) return
        val move = stepPerFrame * frames
        offsetSemitones = when {
            abs(offsetSemitones) <= move -> 0f
            offsetSemitones > 0f -> offsetSemitones - move
            else -> offsetSemitones + move
        }
    }
}
