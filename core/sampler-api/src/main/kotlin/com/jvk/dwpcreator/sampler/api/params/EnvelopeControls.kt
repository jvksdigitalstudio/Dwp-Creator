package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.AdsrState
import com.jvk.dwpcreator.sampler.api.state.EnvelopeTarget
import com.jvk.dwpcreator.sampler.api.state.VoiceState

/**
 * Controles de las tres envolventes de voz. Las tres se construyen con la misma
 * plantilla ([build]); sólo cambian los valores de fábrica y el destino
 * ([EnvelopeTarget]).
 */
object EnvelopeControls {

    val AMP: EnvelopeParams = build(EnvelopeTarget.AMP)
    val FILTER: EnvelopeParams = build(EnvelopeTarget.FILTER)
    val PITCH: EnvelopeParams = build(EnvelopeTarget.PITCH)

    val all: List<EnvelopeParams> = listOf(AMP, FILTER, PITCH)

    fun of(target: EnvelopeTarget): EnvelopeParams = when (target) {
        EnvelopeTarget.AMP -> AMP
        EnvelopeTarget.FILTER -> FILTER
        EnvelopeTarget.PITCH -> PITCH
    }

    private fun adsrOf(target: EnvelopeTarget, state: VoiceState): AdsrState = when (target) {
        EnvelopeTarget.AMP -> state.ampEnvelope
        EnvelopeTarget.FILTER -> state.filterEnvelope.adsr
        EnvelopeTarget.PITCH -> state.pitchEnvelope.adsr
    }

    private fun withAdsr(target: EnvelopeTarget, state: VoiceState, adsr: AdsrState): VoiceState = when (target) {
        EnvelopeTarget.AMP -> state.copy(ampEnvelope = adsr)
        EnvelopeTarget.FILTER -> state.copy(filterEnvelope = state.filterEnvelope.copy(adsr = adsr))
        EnvelopeTarget.PITCH -> state.copy(pitchEnvelope = state.pitchEnvelope.copy(adsr = adsr))
    }

    private fun amountOf(target: EnvelopeTarget, state: VoiceState): Float = when (target) {
        EnvelopeTarget.FILTER -> state.filterEnvelope.amount
        EnvelopeTarget.PITCH -> state.pitchEnvelope.amount
        EnvelopeTarget.AMP -> 1f
    }

    private fun withAmount(target: EnvelopeTarget, state: VoiceState, amount: Float): VoiceState = when (target) {
        EnvelopeTarget.FILTER -> state.copy(filterEnvelope = state.filterEnvelope.copy(amount = amount))
        EnvelopeTarget.PITCH -> state.copy(pitchEnvelope = state.pitchEnvelope.copy(amount = amount))
        EnvelopeTarget.AMP -> state
    }

    private fun build(t: EnvelopeTarget): EnvelopeParams {
        val p = t.keyPrefix
        val d = t.defaults
        return EnvelopeParams(
            target = t,
            attack = VoiceParam(
                "${p}_ATTACK", "Attack", ParamScale(t.minAttackMs, 10_000f, d.attackMs, FxCurve.LOG), FxUnit.MILLISECONDS,
                reader = { adsrOf(t, it).attackMs },
                writer = { s, v -> withAdsr(t, s, adsrOf(t, s).copy(attackMs = v)) }
            ),
            decay = VoiceParam(
                "${p}_DECAY", "Decay", ParamScale(10f, 20_000f, d.decayMs, FxCurve.LOG), FxUnit.MILLISECONDS,
                reader = { adsrOf(t, it).decayMs },
                writer = { s, v -> withAdsr(t, s, adsrOf(t, s).copy(decayMs = v)) }
            ),
            sustain = VoiceParam(
                "${p}_SUSTAIN", "Sustain", ParamScale(0f, 1f, d.sustain), FxUnit.PERCENT,
                reader = { adsrOf(t, it).sustain },
                writer = { s, v -> withAdsr(t, s, adsrOf(t, s).copy(sustain = v)) }
            ),
            release = VoiceParam(
                "${p}_RELEASE", "Release", ParamScale(5f, 20_000f, d.releaseMs, FxCurve.LOG), FxUnit.MILLISECONDS,
                reader = { adsrOf(t, it).releaseMs },
                writer = { s, v -> withAdsr(t, s, adsrOf(t, s).copy(releaseMs = v)) }
            ),
            amount = if (!t.hasAmount) null else VoiceParam(
                "${p}_AMOUNT", "Amount", ParamScale(-1f, 1f, 0f), FxUnit.PERCENT,
                reader = { amountOf(t, it) },
                writer = { s, v -> withAmount(t, s, v) }
            )
        )
    }
}
