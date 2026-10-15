package com.jvk.dwpcreator.sampler.api.state

import com.jvk.dwpcreator.sampler.api.rhythm.DelaySync

/**
 * Estado completo, **inmutable**, del panel del sampler: el bus maestro
 * (MASTER, FX DRIVE A/B, FX DELAY, FX REVERB, FX CHORUS -- la organización de
 * la pestaña PROGRAM de DirectWave de escritorio) y, en [voice], los ajustes
 * de síntesis por voz (muestra, filtro, envolventes, LFO).
 *
 * Inmutable a propósito: la UI y el ViewModel producen una instancia nueva
 * con `copy()` y el hilo de audio simplemente lee la referencia vigente
 * (`@Volatile`) al inicio de cada bloque -- sin locks y sin que el hilo de
 * audio pueda ver nunca un estado a medio escribir.
 *
 * Los valores por defecto de reverb y chorus son los que muestra DirectWave
 * al abrirse (Room 25 %, Damp 11.25 kHz, Diffusion 75 %, Decay 1.29 s;
 * Delay 3.09 ms, Depth 6 ms, Rate 1.25 Hz, Feedback 0 %). **Todos los
 * efectos arrancan apagados**: con el estado por defecto el sonido es
 * idéntico al de antes de existir este bus (ganancia 0 dB, nada insertado).
 */
data class SamplerFxState(
    // MASTER
    val masterVolumeDb: Float = 0f,
    val monophonic: Boolean = false,

    // FX DRIVE A (antes de los efectos) y B (después, asimétrico)
    val driveAEnabled: Boolean = false,
    val driveAAmount: Float = 0f,
    val driveAToneHz: Float = 20_000f,
    val driveBEnabled: Boolean = false,
    val driveBAmount: Float = 0f,
    val driveBToneHz: Float = 20_000f,

    // FX CHORUS
    val chorusEnabled: Boolean = false,
    val chorusDelayMs: Float = 3.09f,
    val chorusDepthMs: Float = 6f,
    val chorusRateHz: Float = 1.25f,
    val chorusFeedback: Float = 0f,
    val chorusMix: Float = 0.5f,
    val chorusWidth: Float = 1f,

    // FX DELAY
    val delayEnabled: Boolean = false,
    val delayTimeMs: Float = 375f,
    val delayFeedback: Float = 0.5f,
    val delayLowCutHz: Float = 120f,
    val delayHighCutHz: Float = 8000f,
    val delayBounce: Boolean = true,
    val delayMix: Float = 0.35f,
    /** `true` = el tiempo sale de [delayBpm] y [delayDivision]; `false` = [delayTimeMs] libre. */
    val delaySync: Boolean = false,
    val delayBpm: Float = 120f,
    /** Índice en [DelaySync.NAMES]. */
    val delayDivision: Int = DelaySync.DEFAULT_INDEX,
    /** Modo cinta: saturación en el bucle + wow/flutter ([delayWow]). */
    val delayTape: Boolean = false,
    val delayWow: Float = 0.3f,

    // FX REVERB
    val reverbEnabled: Boolean = false,
    val reverbRoom: Float = 0.25f,
    val reverbDampHz: Float = 11_250f,
    val reverbDiffusion: Float = 0.75f,
    val reverbDecaySec: Float = 1.29f,
    val reverbMix: Float = 0.3f,
    val reverbPreDelayMs: Float = 0f,
    val reverbModulation: Float = 0.2f,
    val reverbWidth: Float = 1f,

    // SÍNTESIS POR VOZ (muestra, filtro, envolventes, LFO) -- ver [VoiceState]
    val voice: VoiceState = VoiceState()
) {
    /** Copia del estado con la parte de voz transformada por [transform]. */
    fun withVoice(transform: (VoiceState) -> VoiceState): SamplerFxState = copy(voice = transform(voice))
}
