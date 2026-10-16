package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.curve.LfoWaveforms
import kotlin.math.min

/**
 * Oscilador de LFO de una voz. Se **reinicia con cada nota** (fase inicial y
 * fundido de entrada propios) y se evalúa a ritmo de control: [advance] salta
 * varias muestras de una vez. Los valores aleatorios salen de un generador
 * xorshift propio (determinista a partir de la semilla de la voz), sin asignar
 * memoria ni compartir estado entre voces.
 */
internal class LfoOscillator {
    private var phase = 0f
    private var elapsedFrames = 0f
    private var held = 0f
    private var next = 0f
    private var rng = 1

    /** Última salida bipolar, con el fundido de entrada ya aplicado. */
    var output: Float = 0f
        private set

    fun restart(settings: LfoSettings, seed: Int) {
        phase = settings.startPhase
        elapsedFrames = 0f
        rng = (seed * GOLDEN_RATIO_HASH) or 1
        held = random()
        next = random()
        output = 0f
    }

    /** Avanza [frames] muestras con [settings] y devuelve la salida. */
    fun advance(frames: Int, settings: LfoSettings): Float {
        elapsedFrames += frames
        phase += settings.phasePerFrame * frames
        if (settings.oneShot) {
            if (phase > ONE_SHOT_END) phase = ONE_SHOT_END
        } else {
            while (phase >= 1f) {
                phase -= 1f
                held = next
                next = random()
            }
        }
        val fade = if (settings.fadeFrames <= 0f) 1f else min(1f, elapsedFrames / settings.fadeFrames)
        output = LfoWaveforms.value(settings.shape, phase, held, next) * fade
        return output
    }

    private fun random(): Float {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return (x ushr 8) * (2f / 16_777_216f) - 1f
    }

    private companion object {
        /** Fase final del one-shot: el último valor de la onda, sin dar la vuelta. */
        const val ONE_SHOT_END = 0.99999f
        const val GOLDEN_RATIO_HASH = -1640531535 // 0x9E3779B1
    }
}
