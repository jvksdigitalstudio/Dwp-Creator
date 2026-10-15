package com.jvk.dwpcreator.sampler.api.preset

import com.jvk.dwpcreator.sampler.api.params.FilterControls
import com.jvk.dwpcreator.sampler.api.params.LfoControls
import com.jvk.dwpcreator.sampler.api.params.LfoParams
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import com.jvk.dwpcreator.sampler.api.state.LfoDestination
import com.jvk.dwpcreator.sampler.api.state.LfoRateMode
import com.jvk.dwpcreator.sampler.api.state.LfoShape

/**
 * Presets de fábrica de la **síntesis por voz** (filtro y LFO). Sólo datos: las
 * operaciones viven en [FxPresets]. Las envolventes, la muestra y la afinación
 * no llevan presets: dependen demasiado del material.
 */
internal object VoicePresetCatalog {

    private fun filter(name: String, mode: FilterMode, cutoff: Float, resonance: Float) = FxPreset(
        name,
        mapOf(
            FilterControls.MODE to mode.ordinal.toFloat(),
            FilterControls.CUTOFF to cutoff,
            FilterControls.RESONANCE to resonance
        )
    )

    val FILTER_PRESETS: List<FxPreset> = listOf(
        filter("Warm LP", FilterMode.LOWPASS_12, 0.62f, 0.15f),
        filter("Dark LP 24", FilterMode.LOWPASS_24, 0.50f, 0.10f),
        filter("Resonant LP", FilterMode.LOWPASS_12, 0.55f, 0.60f),
        filter("Telephone", FilterMode.BANDPASS, 0.62f, 0.30f),
        filter("Thin HP", FilterMode.HIGHPASS_12, 0.45f, 0.10f),
        filter("Vowel Notch", FilterMode.NOTCH, 0.60f, 0.50f)
    )

    private fun lfo(
        target: LfoParams,
        name: String,
        shape: LfoShape,
        destination: LfoDestination,
        amount: Float,
        mode: LfoRateMode = LfoRateMode.HERTZ,
        rateHz: Float = 5f,
        division: Int = 4,
        fadeMs: Float = 0f
    ) = FxPreset(
        name,
        mapOf(
            target.mode to mode.ordinal.toFloat(),
            target.rate to rateHz,
            target.division to division.toFloat(),
            target.shape to shape.ordinal.toFloat(),
            target.fade to fadeMs,
            target.amount to amount,
            target.destination to destination.ordinal.toFloat()
        )
    )

    /** Los mismos seis presets para cualquiera de los dos LFO. */
    private fun lfoPresets(target: LfoParams): List<FxPreset> = listOf(
        lfo(target, "Vibrato", LfoShape.SINE, LfoDestination.PITCH, amount = 0.20f, rateHz = 5.5f, fadeMs = 400f),
        lfo(target, "Tremolo", LfoShape.SINE, LfoDestination.LEVEL, amount = 0.60f, rateHz = 6f),
        lfo(target, "Auto-Pan", LfoShape.SINE, LfoDestination.PAN, amount = 0.80f, rateHz = 0.8f),
        lfo(target, "Wobble 1/8", LfoShape.TRIANGLE, LfoDestination.FILTER, amount = 0.60f, mode = LfoRateMode.TEMPO, division = 5),
        lfo(target, "Random Filter", LfoShape.SAMPLE_HOLD, LfoDestination.FILTER, amount = 0.50f, rateHz = 4f),
        lfo(target, "Slow Drift", LfoShape.SMOOTH_RANDOM, LfoDestination.PITCH, amount = 0.12f, rateHz = 0.2f)
    )

    val LFO_1_PRESETS: List<FxPreset> = lfoPresets(LfoControls.LFO_1)
    val LFO_2_PRESETS: List<FxPreset> = lfoPresets(LfoControls.LFO_2)
}
