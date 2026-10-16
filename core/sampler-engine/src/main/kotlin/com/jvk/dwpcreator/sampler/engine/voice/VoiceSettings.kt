package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.state.LoopMode
import com.jvk.dwpcreator.sampler.api.state.VoiceState

/**
 * [VoiceState] resuelto a lo que necesita el hilo de audio: coeficientes por
 * muestra de las tres envolventes, LFO con su frecuencia efectiva, filtro con su
 * bandera de puenteo y los parámetros de reproducción. Inmutable. Se construye
 * **fuera** del hilo de audio (al cambiar el estado) y el motor sólo lo lee, así
 * que mover un control durante una nota surte efecto en el siguiente bloque de
 * control sin reservar memoria en el hilo de audio.
 */
internal class VoiceSettings private constructor(
    val ampEnvelope: AdsrCoefficients,
    val filterEnvelope: AdsrCoefficients,
    val pitchEnvelope: AdsrCoefficients,
    /** Cantidad bipolar (-1..1) de las envolventes de modulación. */
    val filterEnvelopeAmount: Float,
    val pitchEnvelopeAmount: Float,
    val lfo1: LfoSettings,
    val lfo2: LfoSettings,
    val filter: FilterSettings,

    /** Transposición fija (afinación + fina) en semitonos. */
    val baseSemitones: Float,
    val pitchTrack: Float,
    val bendRangeSemitones: Float,
    val velocitySensitivity: Float,
    val startFraction: Float,
    val loopMode: LoopMode,
    val reverse: Boolean,
    val legato: Boolean,
    /** Duración del glide en muestras; 0 = sin glide. */
    val glideFrames: Float,
    val glideOnlyLegato: Boolean
) {
    companion object {
        fun from(state: VoiceState, sampleRate: Float): VoiceSettings {
            val lfo1 = LfoSettings.from(state.lfo1, state.tempoBpm, sampleRate)
            val lfo2 = LfoSettings.from(state.lfo2, state.tempoBpm, sampleRate)
            val sample = state.sample
            return VoiceSettings(
                ampEnvelope = AdsrCoefficients.from(state.ampEnvelope, sampleRate),
                filterEnvelope = AdsrCoefficients.from(state.filterEnvelope.adsr, sampleRate),
                pitchEnvelope = AdsrCoefficients.from(state.pitchEnvelope.adsr, sampleRate),
                filterEnvelopeAmount = state.filterEnvelope.amount,
                pitchEnvelopeAmount = state.pitchEnvelope.amount,
                lfo1 = lfo1,
                lfo2 = lfo2,
                filter = FilterSettings.from(state.filter, state.filterEnvelope.amount, listOf(lfo1, lfo2)),
                baseSemitones = sample.pitchSemitones + sample.fineCents / 100f,
                pitchTrack = sample.pitchTrack,
                bendRangeSemitones = sample.bendRangeSemitones,
                velocitySensitivity = sample.velocitySensitivity,
                startFraction = sample.startFraction,
                loopMode = sample.loopMode,
                reverse = sample.reverse,
                legato = sample.legato,
                glideFrames = sample.glideMs * 0.001f * sampleRate,
                glideOnlyLegato = sample.glideOnlyLegato
            )
        }
    }
}
