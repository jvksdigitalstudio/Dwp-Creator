package com.jvk.dwpcreator.sampler.api.params

/**
 * Los módulos del panel del sampler que tienen reinicio propio (y, algunos,
 * presets): los cinco del bus de efectos y los siete de la síntesis por voz.
 * Cada uno declara **qué parámetros e interruptores le pertenecen**: es la
 * única definición que usan los presets, el reinicio de módulo y la detección de
 * "Custom" (nada está duplicado en la UI).
 *
 * [power] es el interruptor de encendido del módulo, o `null` si el módulo no se
 * puede apagar (envolventes, muestra, LFO); **no** forma parte de sus ajustes (un
 * preset no se considera "distinto" por estar el módulo apagado).
 */
enum class FxModule(
    val title: String,
    val power: SamplerToggle?,
    val params: List<SamplerParam>,
    val toggles: List<SamplerToggle> = emptyList()
) {
    DRIVE_A("FX DRIVE A", FxToggle.DRIVE_A, listOf(FxParam.DRIVE_A_AMOUNT, FxParam.DRIVE_A_TONE)),
    DRIVE_B("FX DRIVE B", FxToggle.DRIVE_B, listOf(FxParam.DRIVE_B_AMOUNT, FxParam.DRIVE_B_TONE)),
    CHORUS(
        "FX CHORUS", FxToggle.CHORUS,
        listOf(
            FxParam.CHORUS_DELAY, FxParam.CHORUS_DEPTH, FxParam.CHORUS_RATE,
            FxParam.CHORUS_FEEDBACK, FxParam.CHORUS_MIX, FxParam.CHORUS_WIDTH
        )
    ),
    DELAY(
        "FX DELAY", FxToggle.DELAY,
        listOf(
            FxParam.DELAY_TIME, FxParam.DELAY_DIVISION, FxParam.DELAY_BPM, FxParam.DELAY_FEEDBACK,
            FxParam.DELAY_LOW_CUT, FxParam.DELAY_HIGH_CUT, FxParam.DELAY_WOW, FxParam.DELAY_MIX
        ),
        listOf(FxToggle.DELAY_BOUNCE, FxToggle.DELAY_SYNC, FxToggle.DELAY_TAPE)
    ),
    REVERB(
        "FX REVERB", FxToggle.REVERB,
        listOf(
            FxParam.REVERB_ROOM, FxParam.REVERB_DAMP, FxParam.REVERB_DIFFUSION, FxParam.REVERB_DECAY,
            FxParam.REVERB_PREDELAY, FxParam.REVERB_MODULATION, FxParam.REVERB_WIDTH, FxParam.REVERB_MIX
        )
    ),

    SAMPLE("SAMPLE", null, SampleControls.params, SampleControls.toggles),
    AMP_ENV("AMP ENV", null, EnvelopeControls.AMP.params),
    FILTER("FILTER", FilterControls.ENABLED, FilterControls.params),
    FILTER_ENV("FILTER ENV", null, EnvelopeControls.FILTER.params),
    PITCH_ENV("PITCH ENV", null, EnvelopeControls.PITCH.params),
    LFO_1("LFO 1", null, LfoControls.LFO_1.params),
    LFO_2("LFO 2", null, LfoControls.LFO_2.params)
}
