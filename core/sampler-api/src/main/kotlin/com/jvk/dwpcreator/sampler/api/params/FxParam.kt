package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.rhythm.DelaySync
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/**
 * Descriptor de un parámetro numérico del bus: rango, valor de fábrica,
 * curva del knob, unidad de visualización y cómo se lee/escribe en
 * [SamplerFxState]. Implementa [SamplerParam]: la escala, el formato y el
 * guardado de valores los comparte con los parámetros de voz.
 *
 * Con [FxCurve.LOG], [min] debe ser > 0.
 */
enum class FxParam(
    override val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val curve: FxCurve,
    override val unit: FxUnit,
    private val reader: (SamplerFxState) -> Float,
    private val writer: (SamplerFxState, Float) -> SamplerFxState,
    /** `0` = continuo. `n > 0` = parámetro escalonado de `n` intervalos (`n + 1` posiciones), p. ej. la división rítmica del delay. */
    val steps: Int = 0,
    /** Etiquetas de las opciones si [unit] es [FxUnit.CHOICE]; `null` en el resto. */
    override val labels: List<String>? = null
) : SamplerParam {
    MASTER_VOLUME("Volume", -48f, 6f, 0f, FxCurve.LINEAR, FxUnit.DECIBEL,
        { it.masterVolumeDb }, { s, v -> s.copy(masterVolumeDb = v) }),

    DRIVE_A_AMOUNT("Amount", 0f, 1f, 0f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.driveAAmount }, { s, v -> s.copy(driveAAmount = v) }),
    DRIVE_A_TONE("Tone", 500f, 20_000f, 20_000f, FxCurve.LOG, FxUnit.HERTZ,
        { it.driveAToneHz }, { s, v -> s.copy(driveAToneHz = v) }),
    DRIVE_B_AMOUNT("Amount", 0f, 1f, 0f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.driveBAmount }, { s, v -> s.copy(driveBAmount = v) }),
    DRIVE_B_TONE("Tone", 500f, 20_000f, 20_000f, FxCurve.LOG, FxUnit.HERTZ,
        { it.driveBToneHz }, { s, v -> s.copy(driveBToneHz = v) }),

    CHORUS_DELAY("Delay", 0.5f, 20f, 3.09f, FxCurve.LINEAR, FxUnit.MILLISECONDS,
        { it.chorusDelayMs }, { s, v -> s.copy(chorusDelayMs = v) }),
    CHORUS_DEPTH("Depth", 0f, 15f, 6f, FxCurve.LINEAR, FxUnit.MILLISECONDS,
        { it.chorusDepthMs }, { s, v -> s.copy(chorusDepthMs = v) }),
    CHORUS_RATE("Rate", 0.05f, 10f, 1.25f, FxCurve.LOG, FxUnit.HERTZ,
        { it.chorusRateHz }, { s, v -> s.copy(chorusRateHz = v) }),
    CHORUS_FEEDBACK("Feedback", 0f, 0.9f, 0f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.chorusFeedback }, { s, v -> s.copy(chorusFeedback = v) }),
    CHORUS_MIX("Mix", 0f, 1f, 0.5f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.chorusMix }, { s, v -> s.copy(chorusMix = v) }),
    CHORUS_WIDTH("Width", 0f, 1.5f, 1f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.chorusWidth }, { s, v -> s.copy(chorusWidth = v) }),

    DELAY_TIME("Time", 10f, 2000f, 375f, FxCurve.LOG, FxUnit.MILLISECONDS,
        { it.delayTimeMs }, { s, v -> s.copy(delayTimeMs = v) }),
    DELAY_FEEDBACK("Feedback", 0f, 0.95f, 0.5f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.delayFeedback }, { s, v -> s.copy(delayFeedback = v) }),
    DELAY_LOW_CUT("Low cut", 20f, 2000f, 120f, FxCurve.LOG, FxUnit.HERTZ,
        { it.delayLowCutHz }, { s, v -> s.copy(delayLowCutHz = v) }),
    DELAY_HIGH_CUT("High cut", 500f, 20000f, 8000f, FxCurve.LOG, FxUnit.HERTZ,
        { it.delayHighCutHz }, { s, v -> s.copy(delayHighCutHz = v) }),
    DELAY_MIX("Mix", 0f, 1f, 0.35f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.delayMix }, { s, v -> s.copy(delayMix = v) }),
    DELAY_BPM("BPM", 40f, 240f, 120f, FxCurve.LINEAR, FxUnit.BPM,
        { it.delayBpm }, { s, v -> s.copy(delayBpm = v) }),
    DELAY_DIVISION("Division", 0f, DelaySync.LAST_INDEX.toFloat(), DelaySync.DEFAULT_INDEX.toFloat(), FxCurve.LINEAR, FxUnit.CHOICE,
        { it.delayDivision.toFloat() }, { s, v -> s.copy(delayDivision = DelaySync.indexOf(v)) },
        steps = DelaySync.LAST_INDEX, labels = DelaySync.NAMES),
    DELAY_WOW("Wow", 0f, 1f, 0.3f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.delayWow }, { s, v -> s.copy(delayWow = v) }),

    REVERB_ROOM("Room", 0f, 1f, 0.25f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbRoom }, { s, v -> s.copy(reverbRoom = v) }),
    REVERB_DAMP("Damp", 1000f, 20000f, 11_250f, FxCurve.LOG, FxUnit.HERTZ,
        { it.reverbDampHz }, { s, v -> s.copy(reverbDampHz = v) }),
    REVERB_DIFFUSION("Diffusion", 0f, 1f, 0.75f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbDiffusion }, { s, v -> s.copy(reverbDiffusion = v) }),
    REVERB_DECAY("Decay", 0.1f, 20f, 1.29f, FxCurve.LOG, FxUnit.SECONDS,
        { it.reverbDecaySec }, { s, v -> s.copy(reverbDecaySec = v) }),
    REVERB_MIX("Mix", 0f, 1f, 0.3f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbMix }, { s, v -> s.copy(reverbMix = v) }),
    REVERB_PREDELAY("Pre-dly", 0f, 250f, 0f, FxCurve.LINEAR, FxUnit.MILLISECONDS,
        { it.reverbPreDelayMs }, { s, v -> s.copy(reverbPreDelayMs = v) }),
    REVERB_MODULATION("Mod", 0f, 1f, 0.2f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbModulation }, { s, v -> s.copy(reverbModulation = v) }),
    REVERB_WIDTH("Width", 0f, 1.5f, 1f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbWidth }, { s, v -> s.copy(reverbWidth = v) });

    override val key: String get() = name
    override val scale: ParamScale = ParamScale(min, max, default, curve, steps)

    override fun read(state: SamplerFxState): Float = reader(state)

    override fun apply(state: SamplerFxState, value: Float): SamplerFxState = writer(state, value)
}
