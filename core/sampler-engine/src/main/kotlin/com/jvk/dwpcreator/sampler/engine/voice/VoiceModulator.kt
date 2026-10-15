package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.state.LfoDestination
import kotlin.math.abs

/**
 * Fuentes de modulación **variables en el tiempo** de una voz: envolvente de
 * filtro, envolvente de afinación y dos LFO. Cada [update] avanza todo un bloque
 * de control y deja en sus propiedades la contribución total a cada destino; la
 * voz las suma a sus valores base. No sabe de audio ni de la muestra.
 *
 * Escalas de la cantidad al 100 %:
 * - envolvente de filtro: [FILTER_ENVELOPE_OCTAVES] octavas de corte;
 * - envolvente de afinación: [PITCH_ENVELOPE_SEMITONES] semitonos;
 * - LFO -> corte: [LFO_FILTER_OCTAVES] octavas; -> resonancia: [LFO_RESONANCE];
 *   -> pan: toda la imagen; -> nivel: del 100 % al 0 %; -> afinación:
 *   [LFO_PITCH_SEMITONES] semitonos con **curva cuadrática** (el vibrato fino,
 *   que es el útil, queda en la primera mitad del recorrido).
 */
internal class VoiceModulator {
    private val filterEnvelope = Adsr()
    private val pitchEnvelope = Adsr()
    private val lfo1 = LfoOscillator()
    private val lfo2 = LfoOscillator()

    /** Semitonos que suman la envolvente de afinación y los LFO de afinación. */
    var pitchSemitones: Float = 0f
        private set

    /** Octavas que suman la envolvente de filtro y los LFO de corte. */
    var cutoffOctaves: Float = 0f
        private set

    /** Suma a la resonancia (-0,5..0,5 por LFO). */
    var resonance: Float = 0f
        private set

    /** Factor multiplicativo del nivel (0..1). */
    var level: Float = 1f
        private set

    /** Desplazamiento del paneo (-1..1 por LFO). */
    var pan: Float = 0f
        private set

    fun start(settings: VoiceSettings, seed: Int) {
        filterEnvelope.reset()
        filterEnvelope.trigger()
        pitchEnvelope.reset()
        pitchEnvelope.trigger()
        lfo1.restart(settings.lfo1, seed)
        lfo2.restart(settings.lfo2, seed * 31 + 7)
        pitchSemitones = 0f
        cutoffOctaves = 0f
        resonance = 0f
        level = 1f
        pan = 0f
    }

    fun release() {
        filterEnvelope.release()
        pitchEnvelope.release()
    }

    /** Avanza [frames] muestras todas las fuentes y recalcula las contribuciones. */
    fun update(frames: Int, settings: VoiceSettings) {
        repeat(frames) {
            filterEnvelope.next(settings.filterEnvelope)
            pitchEnvelope.next(settings.pitchEnvelope)
        }
        pitchSemitones = pitchEnvelope.value * settings.pitchEnvelopeAmount * PITCH_ENVELOPE_SEMITONES
        cutoffOctaves = filterEnvelope.value * settings.filterEnvelopeAmount * FILTER_ENVELOPE_OCTAVES
        resonance = 0f
        level = 1f
        pan = 0f
        applyLfo(lfo1, settings.lfo1, frames)
        applyLfo(lfo2, settings.lfo2, frames)
    }

    /** Suma la contribución de un LFO a su destino. Sin asignar memoria: se llama desde el hilo de audio. */
    private fun applyLfo(oscillator: LfoOscillator, lfo: LfoSettings, frames: Int) {
        if (!lfo.active) return
        val out = oscillator.advance(frames, lfo)
        val amount = lfo.amount
        when (lfo.destination) {
            LfoDestination.PITCH -> pitchSemitones += out * amount * abs(amount) * LFO_PITCH_SEMITONES
            LfoDestination.FILTER -> cutoffOctaves += out * amount * LFO_FILTER_OCTAVES
            LfoDestination.RESONANCE -> resonance += out * amount * LFO_RESONANCE
            LfoDestination.PAN -> pan += out * amount
            LfoDestination.LEVEL -> {
                val signed = if (amount < 0f) -out else out
                level *= 1f - abs(amount) * 0.5f * (1f - signed)
            }
        }
    }

    companion object {
        const val FILTER_ENVELOPE_OCTAVES = 8f
        const val PITCH_ENVELOPE_SEMITONES = 24f
        const val LFO_FILTER_OCTAVES = 5f
        const val LFO_RESONANCE = 0.5f
        const val LFO_PITCH_SEMITONES = 12f
    }
}
