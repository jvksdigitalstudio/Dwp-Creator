package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.rhythm.LfoSync
import com.jvk.dwpcreator.sampler.api.state.LfoState
import com.jvk.dwpcreator.sampler.api.state.VoiceState

/**
 * Controles de los dos LFO de voz, construidos con la misma plantilla
 * ([build]). El tempo de sincronía ([TEMPO]) es único y compartido.
 */
object LfoControls {

    val LFO_1: LfoParams = build(1)
    val LFO_2: LfoParams = build(2)

    val all: List<LfoParams> = listOf(LFO_1, LFO_2)

    val TEMPO = VoiceParam(
        "LFO_TEMPO", "BPM", ParamScale(40f, 240f, 120f), FxUnit.BPM,
        reader = { it.tempoBpm },
        writer = { s, v -> s.copy(tempoBpm = v) }
    )

    fun of(index: Int): LfoParams = if (index == 1) LFO_1 else LFO_2

    private fun lfoOf(index: Int, state: VoiceState): LfoState = if (index == 1) state.lfo1 else state.lfo2

    private fun withLfo(index: Int, state: VoiceState, lfo: LfoState): VoiceState =
        if (index == 1) state.copy(lfo1 = lfo) else state.copy(lfo2 = lfo)

    private fun build(i: Int): LfoParams {
        val p = "LFO${i}_"
        val defaults = lfoOf(i, VoiceState())
        return LfoParams(
            index = i,
            mode = VoiceParam.choice(
                "${p}MODE", "Mode", defaults.rateMode,
                get = { lfoOf(i, it).rateMode },
                set = { s, v -> withLfo(i, s, lfoOf(i, s).copy(rateMode = v)) }
            ),
            rate = VoiceParam(
                "${p}RATE", "Rate", ParamScale(0.03f, 30f, defaults.rateHz, FxCurve.LOG), FxUnit.HERTZ,
                reader = { lfoOf(i, it).rateHz },
                writer = { s, v -> withLfo(i, s, lfoOf(i, s).copy(rateHz = v)) }
            ),
            division = VoiceParam(
                "${p}DIVISION", "Division",
                ParamScale(0f, LfoSync.LAST_INDEX.toFloat(), defaults.division.toFloat(), steps = LfoSync.LAST_INDEX),
                FxUnit.CHOICE, LfoSync.NAMES,
                reader = { lfoOf(i, it).division.toFloat() },
                writer = { s, v -> withLfo(i, s, lfoOf(i, s).copy(division = LfoSync.indexOf(v))) }
            ),
            shape = VoiceParam.choice(
                "${p}SHAPE", "Shape", defaults.shape,
                get = { lfoOf(i, it).shape },
                set = { s, v -> withLfo(i, s, lfoOf(i, s).copy(shape = v)) }
            ),
            phase = VoiceParam(
                "${p}PHASE", "Phase", ParamScale(0f, 1f, defaults.phase), FxUnit.DEGREES,
                reader = { lfoOf(i, it).phase },
                writer = { s, v -> withLfo(i, s, lfoOf(i, s).copy(phase = v)) }
            ),
            fade = VoiceParam(
                "${p}FADE", "Fade In", ParamScale(0f, 10_000f, defaults.fadeMs, FxCurve.SQUARED), FxUnit.MILLISECONDS,
                reader = { lfoOf(i, it).fadeMs },
                writer = { s, v -> withLfo(i, s, lfoOf(i, s).copy(fadeMs = v)) }
            ),
            amount = VoiceParam(
                "${p}AMOUNT", "Amount", ParamScale(-1f, 1f, defaults.amount), FxUnit.PERCENT,
                reader = { lfoOf(i, it).amount },
                writer = { s, v -> withLfo(i, s, lfoOf(i, s).copy(amount = v)) }
            ),
            destination = VoiceParam.choice(
                "${p}DEST", "Dest", defaults.destination,
                get = { lfoOf(i, it).destination },
                set = { s, v -> withLfo(i, s, lfoOf(i, s).copy(destination = v)) }
            )
        )
    }
}
